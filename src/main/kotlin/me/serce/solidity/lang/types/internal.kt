package me.serce.solidity.lang.types

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import me.serce.solidity.lang.psi.SolContractDefinition
import me.serce.solidity.lang.psi.SolNamedElement
import me.serce.solidity.lang.psi.SolPsiFactory
import me.serce.solidity.lang.types.SolInteger.Companion.UINT_256

class SolInternalTypeFactory(project: Project) {
  private val psiFactory: SolPsiFactory = SolPsiFactory(project)

  companion object {
    fun of(project: Project): SolInternalTypeFactory {
      return ServiceManager.getService(project, SolInternalTypeFactory::class.java)
    }
  }

  private val registry: Map<String, SolType> by lazy {
    listOf(
      msgType,
      txType,
      blockType,
      abiType
    ).associateBy { it.toString() }
  }

  fun byName(name: String): SolType? = registry[name]

  private val everBuiltinTypes: Map<String, SolNamedElement> by lazy {
    listOf(
      tvmCell,
      tvmSlice,
      tvmBuilder,
      extraCurrencyCollection
    ).associateBy { it.name!! }
  }

  fun builtinByName(name: String): SolNamedElement? = everBuiltinTypes[name]

  val tvmCell: SolContractDefinition by lazy {
    psiFactory.createContract("""
          contract TvmCell {
              function depth() returns(uint16);
              function dataSize(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
//              function dataSizeQ(uint n) returns (optional(uint /*cells*/, uint /*bits*/, uint /*refs*/)); 
              function toSlice() returns (TvmSlice); 
          }
        """)
  }

  val tvmSlice: SolContractDefinition by lazy {
    psiFactory.createContract("""
          contract TvmSlice {
              function empty() returns (bool);
              function size() returns (uint16 /*bits*/, uint8 /*refs*/);
              function bits() returns (uint16);
              function refs() returns (uint8);
              function dataSize(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
              function depth() returns (uint16);
              function hasNBits(uint16 bits) returns (bool); 
              function hasNRefs(uint8 refs) returns (bool); 
              function hasNBitsAndRefs(uint16 bits, uint8 refs) returns (bool);
              function compare(TvmSlice other) returns (int8);
              function decode(TypeA a, TypeB b) returns (TypeA /*a*/, TypeB /*b*/);
//              function decodeQ(TypeA a, TypeB b) returns (optional(TypeA, TypeB)); 
              function loadRef() returns (TvmCell);
              function loadRefAsSlice() returns (TvmSlice);
              function loadSigned(uint16 bitSize) returns (int); 
              function loadUnsigned(uint16 bitSize) returns (uint); 
              function loadTons() returns (uint128); 
              function loadSlice(uint length) returns (TvmSlice);
              function loadSlice(uint length, uint refs) returns (TvmSlice);
              function skip(uint length);
              function skip(uint length, uint refs);
          }
        """)
  }

  val tvmBuilder: SolContractDefinition by lazy {
      psiFactory.createContract("""
            contract TvmBuilder {
                function toSlice() returns (TvmSlice);
                function toCell() returns (TvmCell);
                function size() returns (uint16 /*bits*/, uint8 /*refs*/); 
                function bits() returns (uint16); 
                function refs() returns (uint8); 
                function remBits() returns (uint16); 
                function remRefs() returns (uint8); 
                function remBitsAndRefs() returns (uint16 /*bits*/, uint8 /*refs*/); 
                function depth() returns (uint16); 
                function store(/*list_of_values*/); 
                function storeOnes(uint n); 
                function storeZeroes(uint n); 
                function storeSigned(int256 value, uint16 bitSize); 
                function storeUnsigned(uint256 value, uint16 bitSize); 
                function storeRef(TvmBuilder b); 
                function storeRef(TvmCell c); 
                function storeRef(TvmSlice s); 
                function storeTons(uint128 value); 
            }
          """)
    }

  val extraCurrencyCollection: SolContractDefinition by lazy {
      psiFactory.createContract("""
            contract ExtraCurrencyCollection {
            }
          """)
    }


  val msgType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Msg")} {
          bytes public data;
          uint public gas;
          address public sender;
          uint public value;
          
          ExtraCurrencyCollection currencies;
          
          uint32 createdAt;
          
          TvmSlice data;
          
          bool hasStateInit; 
          
          function pubkey() returns (uint256);
          
          
      }
    """), true)
  }

  val txType: SolType by lazy {
    SolStruct(psiFactory.createStruct("""
      struct ${internalise("Tx")} {
          uint public gasprice;
          address public origin;
          
          uint64 public timestamp;
           
          uint64 public storageFee; 
      }
    """))
  }

  val addressType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Address")} {
          int8 public wid;
          
          uint public value;
          
          uint128 public balance;
          
          ExtraCurrencyCollection public currencies;
      
          function transfer(uint128 value, bool bounce, uint16 flag, TvmCell body, ExtraCurrencyCollection currencies, TvmCell stateInit);
          
          function send(uint value) returns (bool);
          
          function makeAddrStd(int8 wid, uint _address) returns (address);
          
          function makeAddrNone() returns (address);
          
          function makeAddrExtern() returns (address);
          
          function getType() returns (uint8);
          
          function isStdZero() returns (bool);
          
          function isStdAddrWithoutAnyCast() returns (bool);
          
          function isExternZero() returns (bool);
          
          function isNone() returns (bool);
          
          function unpack() returns (int8 /*wid*/, uint256 /*value*/);
      }
    """), true)
  }

  val arrayType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Array")} {
          function push(uint value);
      }
    """), true)
  }

  val abiType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Abi")} {
          // todo varargs
          function encode(TypeA a) returns (TvmCell /*cell*/);
          
          function decode(TvmCell cell) returns (TypeA);
      }
    """), true)
  }

  val blockType: SolType by lazy {
    BuiltinType(internalise("Block"), listOf(
      BuiltinCallable(listOf(), SolAddress, "coinbase", null, Usage.VARIABLE),
      BuiltinCallable(listOf(), UINT_256, "difficulty", null, Usage.VARIABLE),
      BuiltinCallable(listOf(), UINT_256, "gasLimit", null, Usage.VARIABLE),
      BuiltinCallable(listOf(), UINT_256, "number", null, Usage.VARIABLE),
      BuiltinCallable(listOf(), UINT_256, "timestamp", null, Usage.VARIABLE),
      BuiltinCallable(listOf("blockNumber" to UINT_256), SolFixedBytes(32), "blockhash", null, Usage.VARIABLE)
    ))
  }

  val globalType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract Global {
          $blockType block;
          $msgType msg;
          $txType tx;
          $abiType abi;
          uint now;

          function assert(bool condition) private {}
          function require(bool condition) private {}
          function require(bool condition, string message) private {}
          function revert() private {}
          function revert(string) {}
          function keccak256() returns (bytes32) private {}
          function sha3() returns (bytes32) private {}
          function sha256() returns (bytes32) private {}
          function ripemd160() returns (bytes20) private {}
          function ecrecover(bytes32 hash, uint8 v, bytes32 r, bytes32 s) returns (address) private {}
          function addmod(uint x, uint y, uint k) returns (uint) private {}
          function mulmod(uint x, uint y, uint k) returns (uint) private returns (uint) {}
          function selfdestruct(address recipient) private {};
      }
    """), true)
  }
}
