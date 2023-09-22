package com.broxus.solidity

import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager


fun <T> PsiElement.cache(preventRecursion : Boolean = true, block: () -> T) : T? {
  return CachedValuesManager.getCachedValue(this) {
    val result = if (preventRecursion) recursionGuard(this) {block()} else block()
    CachedValueProvider.Result.createSingleDependency(result, this@cache)
  }
}
fun <T> recursionGuard(key: Any, memoize: Boolean = true, block: Computable<T>): T? =
  RecursionManager.doPreventingRecursion(key, memoize, block)

fun <T> Sequence<T>.firstOrElse(el: T): T {
  return firstOrNull() ?: el
}

inline fun <reified R> Sequence<Any?>.firstInstance(): R {
  return first { it is R } as R
}

inline fun <reified R> Sequence<Any?>.firstInstanceOrNull(): R? {
  return firstOrNull { it is R } as? R
}

fun loadCodeSampleResource(ctx: Any, resource: String): String {
  val stream = ctx.javaClass.classLoader.getResourceAsStream(resource)
  // We need to convert line separators here, because IntelliJ always expects \n,
  // while on Windows the resource file will be read with \r\n as line separator.
  return StreamUtil.convertSeparators(StreamUtil.readText(stream, "UTF-8"))
}

fun <T> T?.wrap(): List<T> {
  return when (this) {
    null -> emptyList()
    else -> listOf(this)
  }
}

fun <T> nullIfError(action: () -> T): T? {
  return try {
    action()
  } catch (e: Throwable) {
    null
  }
}

inline fun <reified T: PsiElement> PsiElement.childrenOfType() = this.children.filterIsInstance<T>()
