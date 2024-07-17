package com.broxus.solidity.lang.types

import com.github.yuchi.semver.Range
import org.junit.Assert.assertEquals
import org.junit.Test

class SolIntegerParsingTest {
  @Test
  fun parseInt() {
    assertEquals(SolInteger(false, 256), SolInteger.parse("int"))
    assertEquals(SolInteger(false, 256), SolInteger.parse("int", Range("0.73.0")))
    assertEquals(SolInteger(false, 257), SolInteger.parse("int", Range("0.74.0")))
    assertEquals(SolInteger(false, 257), SolInteger.parse("int", Range("0.75.0")))
  }

  @Test
  fun parseInt8() {
    assertEquals(SolInteger(false, 8), SolInteger.parse("int8"))
  }

  @Test
  fun parseUInt() {
    assertEquals(SolInteger(true, 256), SolInteger.parse("uint"))
  }

  @Test
  fun parseUInt128() {
    assertEquals(SolInteger(true, 128), SolInteger.parse("uint128"))
  }
}
