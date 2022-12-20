package com.broxus.solidity.lang.resolve.ref

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.broxus.solidity.lang.psi.SolElement

interface SolReference : PsiPolyVariantReference {
  override fun getElement(): SolElement

  override fun resolve(): SolElement?

  fun multiResolve(): Collection<PsiElement>
}
