package com.broxus.solidity.ide

import com.intellij.openapi.util.registry.Registry

object RegistryKeys {
    val strictAssignmentValidation: Boolean
        get() = Registry.`is`("broxus.t-sol.assignment.validation.strict")

}
