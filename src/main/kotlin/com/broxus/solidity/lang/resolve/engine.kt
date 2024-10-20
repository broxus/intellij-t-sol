package com.broxus.solidity.lang.resolve

import com.broxus.solidity.childrenOfType
import com.broxus.solidity.ide.hints.isBuiltin
import com.broxus.solidity.lang.core.SolidityFile
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.SolNewExpressionElement
import com.broxus.solidity.lang.psi.impl.getAliases
import com.broxus.solidity.lang.resolve.ref.SolFunctionCallReference
import com.broxus.solidity.lang.resolve.ref.SolReference
import com.broxus.solidity.lang.stubs.SolGotoClassIndex
import com.broxus.solidity.lang.stubs.SolModifierIndex
import com.broxus.solidity.lang.stubs.SolNamedElementIndex
import com.broxus.solidity.lang.types.*
import com.broxus.solidity.nullIfError
import com.broxus.solidity.wrap
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.*
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.Processors
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

object SolResolver {
  fun resolveTypeNameUsingImports(element: PsiElement): Set<SolNamedElement> =
    CachedValuesManager.getCachedValue(element) {
      val commonTargets = setOf(SolContractDefinition::class, SolEnumDefinition::class, SolUserDefinedValueTypeDefinition::class,
        SolImportAlias::class)
      val result = if (element is SolFunctionCallElement) {
        resolveUsingImports(
          commonTargets + setOf(SolErrorDefinition::class, SolEventDefinition::class),
          element,
          element.containingFile
        )
      } else {
        resolveUsingImports(commonTargets + SolStructDefinition::class, element, element.containingFile) +
          resolveBuiltinValueType(element)
      }
      CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
    }


  /**
   * @param withAliases aliases are not recursive, so count them only at the first level of recursion
   */
  private fun resolveUsingImports(
    target: Set<KClass<out SolNamedElement>>,
    element: PsiElement,
    file: PsiFile,
  ): Set<SolNamedElement> {
    // If the elements has no name or text, we can't resolve it.
    val elementName = element.nameOrText ?: return emptySet()

    // Retrieve all PSI elements with the name we're trying to lookup.
    val elements: Collection<SolNamedElement> = StubIndex.getElements( //
      SolNamedElementIndex.KEY, //
      elementName, //
      element.project, //
      null, //
      SolNamedElement::class.java //
    )

    val isAlias = element.parent is SolImportAliasedPair
    val resolvedImportedFiles = collectImports(file).map {
      if (it.names.none { it.containingFile == element.containingFile  }) return@map it
      val hasAlias = isAlias || it.names.none { it is SolImportAlias }
      if (hasAlias) it else ImportRecord(it.file, it.names.filter { it is SolImportAlias  }
      )}
    val sameNameReferences = elements.filter { e -> target.any { it.isSuperclassOf(e::class) } }.filter {
      val containingFile = it.containingFile
      // During completion, IntelliJ copies PSI files, and therefore we need to ensure that we compare
      // files against its original file.
      val originalFile = file.originalFile
      // Below, either include
      containingFile == originalFile || resolvedImportedFiles.any { (containingFile == it.file) && it.names.let {it.isEmpty() || it.any { it.name == elementName }}}
    }


    return sameNameReferences.toSet() + resolveInnerTypes(element, target);
  }

  private val innerMapping : Map<KClass<out SolNamedElement>, (SolContractDefinition) -> List<SolNamedElement>> = mapOf(
    SolEnumDefinition::class to { it.enumDefinitionList },
    SolStructDefinition::class to { it.structDefinitionList },
    SolErrorDefinition::class to { it.errorDefinitionList},
  )
  private fun resolveInnerTypes(element: PsiElement, target: Set<KClass<out SolNamedElement>>): Set<SolNamedElement> {
    return target.flatMap { targetClass ->
      innerMapping[targetClass]?.let { resolveInnerType(element, it) } ?: emptyList()
    }.toSet()
  }

  private val exportElements = setOf(
    SolContractDefinition::class.java,
    SolConstantVariableDeclaration::class.java,
    SolEnumDefinition::class.java,
    SolErrorDefinition::class.java,
    SolStructDefinition::class.java,
  )


  fun collectUsedElements(o: SolImportDirective): List<String> {
    val containingFile = o.containingFile

    val importedNames = collectImportedNames(containingFile)

    val pathes = collectImports(o).map { it.file }
    val importScope = GlobalSearchScope.filesScope(o.project, pathes.map { it.virtualFile })

    val imported = pathes.flatMap {
      CachedValuesManager.getCachedValue(it) {
        val allKeys = HashSet<String>()
        val scope = GlobalSearchScope.fileScope(it)
        StubIndex.getInstance().processAllKeys(SolNamedElementIndex.KEY, Processors.cancelableCollectProcessor(allKeys), scope)
        CachedValueProvider.Result.create(allKeys.filter { StubIndex.getElements(SolNamedElementIndex.KEY, it, scope.project!!, scope, SolNamedElement::class.java).isNotEmpty() }.toSet(), PsiModificationTracker.MODIFICATION_COUNT)
      }
    }

    fun PsiElement.outerIdentifier() = (this as? SolUserDefinedTypeName)?.findIdentifiers()?.takeIf { it.size == 2 }?.firstOrNull()?.nameOrText ?: ""
    val targetNames = importedNames.flatMap {
      ((it.target.outerContract()?.let { listOf(it) } ?: emptyList()) + it.target + it.ref ).mapNotNull { it.name } + it.ref.outerIdentifier()
    }.toSet()
    val used = imported.intersect(targetNames)
      .filter {
        StubIndex.getElements(SolNamedElementIndex.KEY, it, o.project, importScope, SolNamedElement::class.java)
          .all { e -> exportElements.any { it.isAssignableFrom(e.javaClass) } }
      }

    val specificNames = o.importAliasedPairList.flatMap { ((getSolType(it.userDefinedTypeName) as? SolContract)?.let { resolveContractNestedNames(it.ref) } ?: emptyList()) +  it.userDefinedTypeName }.mapNotNull { it.name }.toSet()
    return used.takeIf { specificNames.isEmpty() } ?: used.filter { it in specificNames }
  }

  data class ImportedName(val ref: SolNamedElement, val target: SolNamedElement)

  fun collectImportedNames(root: PsiFile): Set<ImportedName> {
    return CachedValuesManager.getCachedValue(root) {
      val result = root.descendants().filter { it is SolUserDefinedTypeName && it.parentOfType<SolImportDirective>() == null || it is SolVarLiteral }
                  .flatMap { ref -> (ref.reference as? SolReference)?.multiResolve()?.mapNotNull { Pair(ref as? SolNamedElement ?: return@mapNotNull null, it as? SolNamedElement ?: return@mapNotNull null) } ?: emptyList() }
                  .mapNotNull { (ref, it) ->
                    ImportedName(ref, when {
                        it.isBuiltin() -> null
                        it is SolConstructorDefinition -> it.findContract()
                        it.containingFile != root -> it
                        else -> (it.parent as? SolImportAliasedPair)?.userDefinedTypeName
                    } ?: return@mapNotNull null)
                  }.toSet()
      CachedValueProvider.Result.create(result, root)
    }
  }

  data class ImportRecord(val file: PsiFile, val names: List<SolNamedElement>)

  fun collectImports(file: PsiFile): List<ImportRecord> {
    return CachedValuesManager.getCachedValue(file) {
      val result = collectImports(file.childrenOfType<SolImportDirective>()).filter { it.file !== file }
      CachedValueProvider.Result.create(result, result.map { it.file } + file)
    }
  }

  fun collectImports(import: SolImportDirective): List<ImportRecord> {
    return CachedValuesManager.getCachedValue(import) {
      val result = collectImports(listOf(import))
      CachedValueProvider.Result.create(result, result.map { it.file } + import)
    }
  }

  /**
   * Collects imports of all declarations for a given file recursively (except for named imports)
   */
  private fun collectImports(imports: Collection<SolImportDirective>, visited: MutableSet<PsiFile> = hashSetOf()): List<ImportRecord> {
    if (!visited.add((imports.firstOrNull() ?: return emptyList()).containingFile)) {
      return emptyList()
    }

    val (resolvedImportedFiles, concreteResolvedImportedFiles) = imports.partition { it.importAliasedPairList.isEmpty() }.toList()
      .map {
        it.mapNotNull {
          val containingFile = it.importPath?.reference?.resolve()?.containingFile ?: return@mapNotNull null
          val aliases = it.importAliasedPairList
          val names = if (aliases.isNotEmpty()) {
            aliases.mapNotNull { it.importAlias } + aliases.mapNotNull { it.userDefinedTypeName.name?.let { tn -> containingFile.childrenOfType<SolContractDefinition>().find { it.name == tn } } }
          } else containingFile.childrenOfType<SolCallableElement>().toList().flatMap { (if (it is SolContractDefinition) resolveContractMembers(it) else emptyList()) + it }
          ImportRecord(containingFile, names.filterIsInstance<SolNamedElement>())
        }
      }

    val result = concreteResolvedImportedFiles + resolvedImportedFiles
    return result + result.map { collectImports(it.file.childrenOfType<SolImportDirective>(), visited) }.flatten()
  }

  private fun resolveBuiltinValueType(element: PsiElement): Set<SolNamedElement> {
    val id = (element as? SolUserDefinedTypeNameElement)?.findIdentifiers()?.firstOrNull() ?:
        (element as? SolVarLiteral)?.identifier ?: return emptySet()

    return setOf(SolInternalTypeFactory.of(element.project).builtinByName(id.nameOrText ?: return emptySet()) ?: return emptySet())
  }


  private fun <T : SolNamedElement> resolveInnerType(
     element: PsiElement,
     f: (SolContractDefinition) -> List<T>
   ): Set<T> {
     val inheritanceSpecifier = element.parentOfType<SolInheritanceSpecifier>()
     return if (inheritanceSpecifier != null) {
       emptySet()
     } else {
       val names = if (element is SolUserDefinedTypeNameElement) {
         element.findIdentifiers()
       } else {
         element.wrap()
       }
       when {
         names.size > 2 -> emptySet()
         names.size > 1 -> resolveTypeNameUsingImports(names[0])
           .filterIsInstance<SolContractDefinition>()
           .firstOrNull()
           ?.let { resolveInnerType(it, names[1].nameOrText!!, f) }
           ?: emptySet()

         else -> element.parentOfType<SolContractDefinition>()
           ?.let {
             names[0].nameOrText?.let { nameOrText ->
               resolveInnerType(it, nameOrText, f)
             }
           }
           ?: emptySet()
       }
     }
   }

   private val PsiElement.nameOrText
     get() = if (this is PsiNamedElement) {
       this.name
     } else {
       this.text
     }

   private fun <T : SolNamedElement> resolveInnerType(
     contract: SolContractDefinition,
     name: String,
     f: (SolContractDefinition) -> List<T>
   ): Set<T> {
     val supers = contract.collectSupers
       .mapNotNull { it.reference?.resolve() }.filterIsInstance<SolContractDefinition>() + contract
     return supers.flatMap(f)
       .filter { it.name == name }
       .toSet()
   }
  fun resolveTypeName(element: SolReferenceElement): Collection<SolNamedElement> = StubIndex.getElements(
    SolGotoClassIndex.KEY,
    element.referenceName,
    element.project,
    null,
    SolNamedElement::class.java
  )

  fun resolveModifier(modifier: SolModifierInvocationElement): List<SolModifierDefinition> = StubIndex.getElements(
    SolModifierIndex.KEY,
    modifier.firstChild.text,
    modifier.project,
    null,
    SolNamedElement::class.java
  ).filterIsInstance<SolModifierDefinition>()
    .toList()

  fun resolveVarLiteralReference(element: SolNamedElement): List<SolNamedElement> {
    return when {
        element.parent?.parent is SolFunctionCallExpression -> {
          val functionCall = element.findParentOrNull<SolFunctionCallElement>()!!
          val resolved = functionCall.reference?.multiResolve() ?: emptyList()
          if (resolved.isNotEmpty()) {
            resolved.filterIsInstance<SolNamedElement>()
          } else {
            resolveVarLiteral(element)
          }
        }
        element.parent is SolModifierInvocation -> {
          when {
            element.parent.parent is SolConstructorDefinition -> element.findContract()?.collectSupers?.filter { resolveTypeName(it).filterIsInstance<SolContractDefinition>().any { it.name == element.name } }
            else -> (element.parent as SolModifierInvocation).reference?.multiResolve()?.filterIsInstance<SolNamedElement>()?.takeIf { it.isNotEmpty() }
          } ?: emptyList()
        }
        else -> {
          resolveVarLiteral(element)
            .findBest {
              when (it) {
                is SolVariableDeclaration -> 1
                is SolParameterDef -> 10
                is SolStateVariableDeclaration -> 100
                else -> Int.MAX_VALUE
              }
            }
        }
    }
  }

  private fun <T : Any> List<T>.findBest(priorities: (T) -> Int): List<T> {
    return if (this.size == 1) this else
      groupBy { priorities(it) }
      .minByOrNull { it.key }
      ?.value
      ?: emptyList()
  }

  fun resolveVarLiteral(element: SolNamedElement): List<SolNamedElement> {
    return when (element.name) {
      "this" -> element.findContract()
        .wrap()
      "super" -> element.findContract()
        ?.supers
        ?.flatMap { resolveTypeNameUsingImports(it) }
        ?: emptyList()
      else -> lexicalDeclarations(element)
        .filter { it.name == element.name }
        .distinct()
        .toList()
    }
  }

  fun resolveMemberAccess(element: SolMemberAccessExpression): List<SolMember> {
    if (element.parent is SolFunctionCallExpression) {
      val functionCall = element.findParentOrNull<SolFunctionCallElement>()!!
      val resolved = (functionCall.reference as SolFunctionCallReference)
        .resolveFunctionCallAndFilter()
        .filterIsInstance<SolMember>()
      if (resolved.isNotEmpty()) {
        return resolved
      }
    }
    return when (val memberName = element.identifier?.text) {
      null -> emptyList()
      else -> element.getMembers()
        .filter { it.getName() == memberName }
    }
  }

  fun resolveContractMembers(contract: SolContractDefinition, skipThis: Boolean = false): List<SolMember> {
    val members = if (!skipThis)
      CachedValuesManager.getCachedValue(contract) {
        val result = contract.stateVariableDeclarationList as List<SolMember> + contract.functionDefinitionList +
                contract.structDefinitionList.map { SolStructConstructor(it) } + contract.enumDefinitionList.map { SolEnum(it) }
        CachedValueProvider.Result.create(result, if (contract.isBuiltin()) ModificationTracker.NEVER_CHANGED else contract.parent)
      }
    else
      emptyList()
    val supers = contract.supers
            .map { resolveTypeName(it).firstOrNull() }
            .filterIsInstance<SolContractDefinition>()
            .flatMap { resolveContractMembers(it) }
    return if (supers.isEmpty()) members else members + supers
  }

  private fun resolveContractNestedNames(contract: SolContractDefinition, skipThis: Boolean = false): List<SolNamedElement> {
    val members = if (!skipThis) {
      CachedValuesManager.getCachedValue(contract) {
        val result = contract.structDefinitionList + contract.enumDefinitionList + contract.errorDefinitionList + contract.userDefinedValueTypeDefinitionList
        CachedValueProvider.Result.create(result, if (contract.isBuiltin()) ModificationTracker.NEVER_CHANGED else contract.parent)
      }
    } else emptyList()
    return members + contract.supers
      .map { resolveTypeName(it).firstOrNull() }
      .filterIsInstance<SolContractDefinition>()
      .flatMap { resolveContractNestedNames(it) }
  }


  fun lexicalDeclarations(place: PsiElement, stop: (PsiElement) -> Boolean = { false }): Sequence<SolNamedElement> {
    val visitedScopes = hashSetOf<Pair<PsiElement, PsiElement>>()
    return lexicalDeclarations0(visitedScopes, place, stop)
  }

  private fun lexicalDeclarations0(
    visitedScopes: HashSet<Pair<PsiElement, PsiElement>>,
    place: PsiElement,
    stop: (PsiElement) -> Boolean = { false }
  ): Sequence<SolNamedElement> {
    return SolInternalTypeFactory.of(place.project).getDeclarations(place, SolInternalTypeFactory.of(place.project).allDeclarations) + lexicalDeclRec(visitedScopes, place, stop).distinct() + place.getAliases() + resolveTypeNameUsingImports(place)
  }

  private fun lexicalDeclRec(
    visitedScopes: HashSet<Pair<PsiElement, PsiElement>>,
    place: PsiElement,
    stop: (PsiElement) -> Boolean
  ): Sequence<SolNamedElement> {
    return place.ancestors
      .drop(1) // current element might not be a SolElement
      .takeWhileInclusive { it is SolElement && !stop(it) }
      .flatMap { lexicalDeclarations(visitedScopes, it, place) }
  }

  fun lexicalDeclarations(
    visitedScopes: HashSet<Pair<PsiElement, PsiElement>>,
    scope: PsiElement,
    place: PsiElement
  ): Sequence<SolNamedElement> {
    // Note that in some cases, loops are possible to encounter when searching for definitions.
    // To avoid the issue, ensure that we only visit place that haven't been visited before.
    if (!visitedScopes.add(scope to place)) {
      return emptySequence()
    }
    return when (scope) {
      is SolVariableDeclaration -> {
        scope.declarationList?.declarationItemList?.filterIsInstance<SolNamedElement>()?.asSequence()
          ?: scope.typedDeclarationList?.typedDeclarationItemList?.filterIsInstance<SolNamedElement>()?.asSequence()
          ?: sequenceOf(scope)
      }

      is SolVariableDefinition -> {
        if (place.endOffset > scope.endOffset) {
          lexicalDeclarations(visitedScopes, scope.firstChild, place)
        } else emptySequence()
      }

      is SolStateVariableDeclaration -> sequenceOf(scope)
      is SolContractDefinition -> {
        val childrenScope = sequenceOf(
          scope.stateVariableDeclarationList as List<PsiElement>,
          scope.enumDefinitionList,
          scope.structDefinitionList
        ).flatten()
          .map { lexicalDeclarations(visitedScopes, it, place) }
          .flatten() + scope.structDefinitionList + scope.eventDefinitionList + scope.errorDefinitionList + scope.enumDefinitionList
        val extendsScope = scope.supers.asSequence()
          .map { resolveTypeName(it).firstOrNull() }
          .filterNotNull()
          .map { lexicalDeclarations(visitedScopes, it, place) }
          .flatten()
        childrenScope + extendsScope + scope.functionDefinitionList
      }
      is SolFunctionDefinition -> {
        scope.parameters.asSequence() +
          (scope.returns?.parameterDefList?.asSequence() ?: emptySequence())
      }
      is SolConstructorDefinition -> {
        scope.parameterList?.parameterDefList?.asSequence() ?: emptySequence()
      }
      is SolModifierDefinition -> {
        scope.parameterList?.parameterDefList?.asSequence() ?: emptySequence()
      }
      is SolEnumDefinition -> sequenceOf(scope)
      is SolForStatement -> when {
          PsiTreeUtil.isAncestor(scope, place, false) -> {
            scope.children.firstOrNull()
              ?.let { lexicalDeclarations(visitedScopes, it, place) } ?: emptySequence()
          }
          else -> emptySequence()
      }

      is SolStatement -> {
        scope.children.asSequence()
          .map { lexicalDeclarations(visitedScopes, it, place) }
          .flatten()
      }

      is SolBlock -> {
        scope.statementList.asSequence()
          .map { lexicalDeclarations(visitedScopes, it, place) }
          .flatten()
      }

      is SolidityFile -> {
        RecursionManager.doPreventingRecursion(scope.name, true) {
          val contracts = scope.children.asSequence()
            .filterIsInstance<SolContractDefinition>()

          val constantVariables = scope.children.asSequence()
            .filterIsInstance<SolConstantVariable>()

          val freeFunctions = scope.children.asSequence()
            .filterIsInstance<SolFunctionDefinition>()

          val importDirectives = scope.children.asSequence().filterIsInstance<SolImportDirective>().toList()
          val imports = importDirectives.asSequence()
            .mapNotNull {
              if (it.importAliasedPairList.isEmpty()) {
                nullIfError { it.importPath?.reference?.resolve()?.containingFile }
              } else null
            }
            .map { lexicalDeclarations(visitedScopes, it, place) }
            .flatten()
          val specificImports = importDirectives.asSequence()
                      .mapNotNull {
                        it.importAliasedPairList.find { it.importAlias?.name == place.nameOrText }?.userDefinedTypeName
                      }

          imports + specificImports + contracts + constantVariables + freeFunctions
        } ?: emptySequence()
      }

      is SolTupleStatement -> {
        scope.variableDeclaration?.let {
          val declarationList = it.declarationList
          val typedDeclarationList = it.typedDeclarationList
          when {
            declarationList != null -> declarationList.declarationItemList.asSequence()
            typedDeclarationList != null -> typedDeclarationList.typedDeclarationItemList.asSequence()
            else -> sequenceOf(it)
          }
        } ?: emptySequence()
      }

      else -> emptySequence()
    }
  }

  fun resolveNewExpression(parentNew: SolNewExpressionElement): Collection<PsiElement> {
    return parentNew.reference.multiResolve()
  }
}

private fun <T> Sequence<T>.takeWhileInclusive(pred: (T) -> Boolean): Sequence<T> {
  var shouldContinue = true
  return takeWhile {
    val result = shouldContinue
    shouldContinue = pred(it)
    result
  }
}

fun SolCallable.canBeApplied(arguments: SolFunctionCallArguments): Boolean {
  val callArgumentTypes = arguments.expressionList.map { it.type }
  val parseParameters = parseParameters()
  val parameters = parseParameters
    .map { it.second }
  if (parameters.size != callArgumentTypes.size && parseParameters.none { it.first == "varargs" })
    return false
  return !parameters.zip(callArgumentTypes)
    .any { (paramType, argumentType) ->
      paramType != SolUnknown && !paramType.isAssignableFrom(argumentType)
    }
}
