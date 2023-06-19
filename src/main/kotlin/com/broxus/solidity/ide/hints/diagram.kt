package com.broxus.solidity.ide.hints

import com.broxus.solidity.ide.SolidityIcons
import com.broxus.solidity.lang.TSolidityFileType
import com.broxus.solidity.lang.core.SolidityFile
import com.broxus.solidity.lang.psi.ContractType
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.lang.psi.SolNamedElement
import com.broxus.solidity.lang.psi.SolStructDefinition
import com.intellij.diagram.*
import com.intellij.diagram.presentation.DiagramLineType
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement

private const val ID = "T-Sol"

class SolDiagramProvider: DiagramProvider<PsiElement>() {
  override fun getID(): String = ID

  override fun getPresentableName(): String = "T-Sol diagram provider"

  override fun createVisibilityManager(): DiagramVisibilityManager = EmptyDiagramVisibilityManager()

  override fun getElementManager(): DiagramElementManager<PsiElement> {
    return object: AbstractDiagramElementManager<PsiElement>() {
      override fun findInDataContext(context: DataContext): PsiElement? {
//        val project = CommonDataKeys.PROJECT.getData(context) ?: return null
        val initialElement = CommonDataKeys.PSI_ELEMENT.getData(context) ?: return null
        val solElement = when (initialElement) {
          is SolContractDefinition, is SolStructDefinition -> initialElement
          is PsiDirectory -> initialElement
          else -> initialElement.containingFile as? SolidityFile ?: return null
        }
        return solElement
      }

      override fun isAcceptableAsNode(p0: Any?): Boolean {
        return p0 is SolContractDefinition || p0 is SolStructDefinition || p0 is SolidityFile || p0 is PsiDirectory
      }

      override fun getNodeTooltip(p0: PsiElement?): String {
        return "myNodeTooltip"
      }

      override fun getElementTitle(p0: PsiElement?): String {
        return (p0 as? PsiNamedElement)?.name ?: "N/A"
      }
    }
  }

  override fun getVfsResolver(): DiagramVfsResolver<PsiElement> {
    return object: DiagramVfsResolver<PsiElement> {
      override fun getQualifiedName(p0: PsiElement?): String? {
        return when (p0) {
          is PsiDirectory -> p0.virtualFile
          is SolidityFile -> p0.virtualFile
          else -> null
        }?.url
      }

      override fun resolveElementByFQN(p0: String, p1: Project): PsiElement? {
        val vFile = VirtualFileManager.getInstance().findFileByUrl(p0) ?: return null

        val manager = PsiManager.getInstance(p1)
        return manager.findFile(vFile) ?: manager.findDirectory(vFile)
      }
    }
  }

  override fun createNodeContentManager(): DiagramNodeContentManager {
    return object: AbstractDiagramNodeContentManager() {
      val category = DiagramCategory({"All"}, SolidityIcons.FILE_ICON)
      override fun getContentCategories(): Array<DiagramCategory> {
        return Array(1) {category}
      }

    }
  }

  override fun getRelationshipManager(): DiagramRelationshipManager<PsiElement> = DiagramRelationshipManager.NO_RELATIONSHIP_MANAGER as DiagramRelationshipManager<PsiElement>

  override fun createDataModel(p0: Project, p1: PsiElement?, p2: VirtualFile?, p3: DiagramPresentationModel): DiagramDataModel<PsiElement> {
    return SolDiagramDataModel(p0, p1)
  }
}

class SolDiagramDataModel(project: Project, val element: PsiElement?) : DiagramDataModel<PsiElement>(project, DiagramProvider.findByID(ID)!!) {

  private val myNodes : MutableSet<DiagramNode<PsiElement>>
  private val myEdges : MutableSet<DiagramEdge<PsiElement>>

  init {
    myNodes = mutableSetOf()
    myEdges = mutableSetOf()
    refreshDataModel()
  }


  override fun dispose() {

  }

  override fun getNodes(): Collection<DiagramNode<PsiElement>> = myNodes

  override fun getEdges(): Collection<DiagramEdge<PsiElement>> = myEdges

  override fun getModificationTracker(): ModificationTracker = this

  override fun addElement(p0: PsiElement?): DiagramNode<PsiElement>? {
    if (p0 != null) {
      myNodes.add(SolDiagramNode(p0, provider))
    }
    return null;
  }

  override fun getNodeName(p0: DiagramNode<PsiElement>): String {
    return "myNodeName$p0"
  }

  override fun refreshDataModel() {
    myNodes.clear()
    myEdges.clear()
    val element = this.element ?: return
    val queue = ArrayDeque<PsiElement>()
    when (element) {
      is PsiDirectory -> {
        VfsUtil.collectChildrenRecursively(element.virtualFile)
                .filter { it.extension == TSolidityFileType.defaultExtension }
                .mapNotNull { PsiManager.getInstance(project).findFile(it) as? SolidityFile }
                .forEach { processSolFile(queue, it) }
      }
        is SolidityFile -> {
          processSolFile(queue, element)
        }

      else -> {
        queue.add(element)
      }
    }
    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      myNodes.add(SolDiagramNode(current, provider))
      (current as? SolContractDefinition)?.let { c ->
        SolSubContractHierarchy(project, c).let { it.getChildElements(it.rootElement) }.filterIsInstance<SolTreeHierarchyDescriptor>().forEach { s ->
          val parentContract = s.psiElement as? SolContractDefinition ?: return@forEach
          val parentNode = addNode(parentContract)
          queue.add(parentContract)
          val relationship = (parentNode as? SolContractDefinition)?.let { when (it.contractType) {
            ContractType.COMMON -> SolDiagramRelationship.EXTENDS
            ContractType.LIBRARY -> SolDiagramRelationship.EXTENDS
            ContractType.INTERFACE -> if (c.contractType == ContractType.INTERFACE) SolDiagramRelationship.EXTENDS else SolDiagramRelationship.REALIZATION
          } } ?: SolDiagramRelationship.EXTENDS
          myEdges.add(SolDiagramEdge(SolDiagramNode(c, provider), SolDiagramNode(parentNode, provider), relationship.relationship))
        }
      }
    }
  }

  private fun processSolFile(queue: ArrayDeque<PsiElement>, element: SolidityFile) {
    val elements = element.children.filter { it is SolContractDefinition || it is SolStructDefinition }
    queue.addAll(elements.filter { !queue.contains(it) })
  }

  private fun addNode(solContractDefinition: SolContractDefinition): PsiElement {
    val element1 = SolDiagramNode(solContractDefinition, provider)
    return (if (myNodes.add(element1)) element1 else myNodes.find { it.identifyingElement == solContractDefinition }!!).identifyingElement
  }
}

enum class SolDiagramRelationship(val relationship: DiagramRelationshipInfo) {
  REALIZATION(DiagramRelationshipInfoAdapter.Builder().setName("REALIZATION").setLineType(DiagramLineType.DASHED).setTargetArrow(DiagramRelationshipInfo.DELTA).create()),
  EXTENDS(DiagramRelationshipInfoAdapter.Builder().setName("DEPENDENCY").setLineType(DiagramLineType.SOLID).setTargetArrow(DiagramRelationshipInfo.DELTA).create())
}

class SolDiagramEdge(source: DiagramNode<PsiElement>, target: DiagramNode<PsiElement>, relationship: DiagramRelationshipInfo ) : DiagramEdgeBase<PsiElement>(source, target, relationship) {

}

class SolDiagramNode(element: PsiElement, provider: DiagramProvider<PsiElement>): PsiDiagramNode<PsiElement>(element, provider) {
  override fun getTooltip(): String? {
    return "myToolTip"
  }
}
class SolSubContractHierarchy(project: Project, element: SolNamedElement) : HierarchyTreeStructure(project, SolTreeHierarchyDescriptor(project, null, element, true)) {
  override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
    val contract = descriptor.psiElement as? SolContractDefinition ?: return emptyArray()
    return contract.supers.mapNotNull { it.reference?.resolve() }.map { SolTreeHierarchyDescriptor(myProject, descriptor, it, false) }.toTypedArray()
  }
}

class SolTreeHierarchyDescriptor(project: Project, parentDescriptor: NodeDescriptor<*>?, element: PsiElement, isBase: Boolean) : HierarchyNodeDescriptor(project, parentDescriptor, element, isBase){

}
