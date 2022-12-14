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
      abiType,
      mathType,
      tvmType,
      mappingType,
      optionalType
    ).associateBy { it.toString() }
  }

  fun byName(name: String): SolType? = registry[name]

  private val everBuiltinTypes: Map<String, SolNamedElement> by lazy {
    listOf(
      tvmCell,
      tvmSlice,
      tvmBuilder,
      extraCurrencyCollection,
    ).associateBy { it.name!! }
  }

  fun builtinByName(name: String): SolNamedElement? = everBuiltinTypes[name]

  val tvmCell: SolContractDefinition by lazy {
    psiFactory.createContract("""
          contract TvmCell {
              function depth() returns(uint16);
              function dataSize(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
              function dataSizeQ(uint n) returns (optional(uint /*cells*/, uint /*bits*/, uint /*refs*/)); 
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
              function decodeQ(TypeA a, TypeB b) returns (optional(TypeA, TypeB)); 
              function loadRef() returns (TvmCell);
              function loadRefAsSlice() returns (TvmSlice);
              function loadSigned(uint16 bitSize) returns (int); 
              function loadUnsigned(uint16 bitSize) returns (uint); 
              function loadTons() returns (uint128); 
              function loadSlice(uint length) returns (TvmSlice);
              function loadSlice(uint length, uint refs) returns (TvmSlice);
              function decodeFunctionParams(functionName) returns (TypeA /*a*/, TypeB /*b*/, ...)
              function decodeFunctionParams(ContractName) returns (TypeA /*a*/, TypeB /*b*/, ...);
              function decodeStateVars(ContractName) returns (uint256 /*pubkey*/, uint64 /*timestamp*/, bool /*constructorFlag*/, Type1 /*var1*/, Type2 /*var2*/); 
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

  val mappingType: SolContract by lazy {
    SolContract(
      psiFactory.createContract(
        """
      contract ${internalise("Mapping")} {
      
          function at(KeyType index) returns (ValueType);
          
          function min() returns (optional(KeyType, ValueType));
          
          function max() returns (optional(KeyType, ValueType));
          
          function next(KeyType key) returns (optional(KeyType, ValueType));
          
          function prev(KeyType key) returns (optional(KeyType, ValueType));
          
          function nextOrEq(KeyType key) returns (optional(KeyType, ValueType));
          
          function prevOrEq(KeyType key) returns (optional(KeyType, ValueType));
          
          function delMin() returns (optional(KeyType, ValueType));
          
          function delMax() returns (optional(KeyType, ValueType));
          
          function fetch(KeyType key) returns (optional(ValueType));
          
          function exists(KeyType key) returns (bool);
          
          function empty() returns (bool);
           
          function replace(KeyType key, ValueType value) returns (bool);
          
          function add(KeyType key, ValueType value) returns (bool);
          
          function getSet(KeyType key, ValueType value) returns (optional(ValueType));
           
          function getAdd(KeyType key, ValueType value) returns (optional(ValueType));
          
          function getReplace(KeyType key, ValueType value) returns (optional(ValueType));
          
          function keys() returns (KeyType[]);
           
          function values() returns (ValueType[]);
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

  val optionalType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Optional")} {

          function hasValue() returns (bool);
          
          function get() returns (Type);
            
          function set(Type value);

          function reset();
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

  val tvmType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Tvm")} {
          // todo varargs
          function accept();
          
          function setGasLimit(uint g);

          function buyGas(uint value);
          
          function commit();

          function rawCommit();
          
          function getData() returns (TvmCell);

          function setData(TvmCell data);
          
          function log(string log);

          function hexdump(T a);
          
          function bindump(T a);

          function setcode(TvmCell newCode);
          
          function configParam(uint8 paramNumber) returns (TypeA a, TypeB b);
           
          function rawConfigParam(uint8 paramNumber) returns (TvmCell cell, bool status); 

          function rawReserve(uint value, uint8 flag);
          
          function rawReserve(uint value, ExtraCurrencyCollection currency, uint8 flag);
          
          function initCodeHash() returns (uint256 hash); 

          function hash(TvmCell cellTree) returns (uint256); 
          
          function hash(string data) returns (uint256); 

          function hash(bytes data) returns (uint256); 
          
          function hash(TvmSlice data) returns (uint256); 

          function checkSign(uint256 hash, uint256 SignHighPart, uint256 SignLowPart, uint256 pubkey) returns (bool); 
          
          function checkSign(uint256 hash, TvmSlice signature, uint256 pubkey) returns (bool); 

          function checkSign(TvmSlice data, TvmSlice signature, uint256 pubkey) returns (bool); 
          
          function insertPubkey(TvmCell stateInit, uint256 pubkey) returns (TvmCell); 

          function buildStateInit(TvmCell code, TvmCell data) returns (TvmCell stateInit); 
          
          function buildStateInit(TvmCell code, TvmCell data, uint8 splitDepth) returns (TvmCell stateInit);
           
          function buildStateInit(TvmCell code, TvmCell data, uint8 splitDepth, uint256 pubkey, Contract contr, VarInit varInit);
           
           function stateInitHash(uint256 codeHash, uint256 dataHash, uint16 codeDepth, uint16 dataDepth) returns (uint256);
           
           function code() returns (TvmCell);
            
           function codeSalt(TvmCell code) returns (optional(TvmCell) optSalt);
           
           function setCodeSalt(TvmCell code, TvmCell salt) returns (TvmCell newCode); 
           
           function pubkey() returns (uint256);
            
           function setPubkey(uint256 newPubkey); 
           
           function setCurrentCode(TvmCell newCode); 
           
           function resetStorage();
            
           function functionId(Function functionName) returns (uint32);
           
           function functionId(Contract ContractName) returns (uint32); 
           
           function encodeBody(functionName, arg0, arg1, arg2) returns (TvmCell);
            
           function encodeBody(functionName, callbackFunction, arg0, arg1, arg2) returns (TvmCell);
           
           function encodeBody(contractName, arg0, arg1, arg2) returns (TvmCell);
           
           function exit();
           
           function exit1();
           
         
          function buildDataInit(uint256 pubkey, contractName, VarInit varInit);
           
           function buildExtMsg(
               address dest,
               uint64 time,
               uint32 expire,
               FunctionIdentifier /*[, list of function arguments]}*/ call,
               bool sign,
               optional(uint256) pubkey,
               uint32 /*| functionIdentifier*/ callbackId,
               uint32 /*| functionIdentifier*/ onErrorId,
               TvmCell stateInit,
               optional(uint32) signBoxHandle,
               uint8 abiVer,
               uint8 flags
           ) returns (TvmCell);
           
           function buildIntMsg(
               address dest,
               uint128 value,
               functionName/*, [callbackFunction,] arg0, arg1, arg2, ...}*/ call,
               bool bounce,
               ExtraCurrencyCollection currencies, 
               TvmCell stateInit 
           )
           returns (TvmCell);
           
           function sendrawmsg(TvmCell msg, uint8 flag);
           
      }
    """), true)
  }


  val mathType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Abi")} {
           // todo varargs
           function min(T a, T b) returns (T);
          
           function max(T a, T b) returns (T);
          
           function minmax(T a, T b) returns (T /*min*/, T /*max*/);
           
           function abs(intM val) returns (intM);
            
           function abs(fixedMxN val) returns (fixedMxN);
             
           function modpow2(uint value, uint power) returns (uint);
            
           function divc(T a, T b) returns (T);
           
           function divr(T a, T b) returns (T);
           
           function muldivmod(T a, T b, T c) returns (T /*result*/, T /*remainder*/);
           
           function muldiv(T a, T b, T c) returns (T);

           function muldivr(T a, T b, T c) returns (T);
                      
           function muldivc(T a, T b, T c) returns (T);
           
           function divmod(T a, T b) returns (T /*result*/, T /*remainder*/);
           
           function sign(int val) returns (int8);
           
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
          $mathType math;
          $tvmType tvm;
          
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
          
          logtvm(string log);
      }
    """), true)
  }
}
