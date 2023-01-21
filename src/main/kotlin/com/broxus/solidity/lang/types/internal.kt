package com.broxus.solidity.lang.types

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.lang.psi.SolNamedElement
import com.broxus.solidity.lang.psi.SolPsiFactory
import com.broxus.solidity.lang.types.SolInteger.Companion.UINT_256

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
      optionalType,
      structType,
      stringType,
      rndType,
      bytesType
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
              /**
                Returns the depth d of the TvmCell c. If c has no references, then d = 0; otherwise d is equal to one plus the maximum of depths of cells referred to from c. If c is a Null instead of a Cell, returns zero.
              */
              function depth() returns(uint16);
              /**
              Returns the number of distinct cells, data bits in the distinct cells and cell references in the distinct cells. If number of the distinct cells exceeds n+1 then a cell overflow exception is thrown. This function is a wrapper for the CDATASIZE opcode (TVM - A.11.7).
              */              
              function dataSize(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
              /**
              Returns the number of distinct cells, data bits in the distinct cells and cell references in the distinct cells. If number of the distinct cells exceeds n+1 then this function returns an optional that has no value. This function is a wrapper for the CDATASIZEQ opcode (TVM - A.11.7).
              */              
              function dataSizeQ(uint n) returns (optional(uint /*cells*/, uint /*bits*/, uint /*refs*/)); 
              /**
              Converts a TvmCell to TvmSlice.
              */              
              function toSlice() returns (TvmSlice); 
          }
        """)
  }

  val tvmSlice: SolContractDefinition by lazy {
    psiFactory.createContract("""
          contract TvmSlice {
              /**
              Checks whether the TvmSlice is empty (i.e., contains no data bits and no cell references).
              */              
              function empty() returns (bool);
              /**
              Returns the number of data bits and references in the TvmSlice.
              */  
              function size() returns (uint16 /*bits*/, uint8 /*refs*/);
              /**
              Returns the number of data bits in the TvmSlice.
              */              
              function bits() returns (uint16);
              /**
              Returns the number of references in the TvmSlice.
              */              
              function refs() returns (uint8);
              /**
              Returns the number of distinct cells, data bits in the distinct cells and cell references in the distinct cells. If number of the distinct cells exceeds n+1 then a cell overflow exception is thrown. Note that the returned count of distinct cells does not take into account the cell that contains the slice itself. This function is a wrapper for SDATASIZE opcode (TVM - A.11.7).
              */              
              function dataSize(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
              /**
              Returns the number of distinct cells, data bits in the distinct cells and cell references in the distinct cells. If number of the distinct cells exceeds n+1 then this function returns an optional that has no value. Note that the returned count of distinct cells does not take into account the cell that contains the slice itself. This function is a wrapper for SDATASIZEQ opcode (TVM - A.11.7).              */              
              function dataSizeQ(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
              /**
              Returns the depth of TvmSlice. If the TvmSlice has no references, then 0 is returned, otherwise function result is one plus the maximum of depths of the cells referred to from the slice.              
              */  
              function depth() returns (uint16);
              /**
              Checks whether the TvmSlice contains the specified amount of data bits and references.
              */              
              function hasNBits(uint16 bits) returns (bool); 
              /**
              Checks whether the TvmSlice contains the specified amount of data bits and references.
              */              
              function hasNRefs(uint8 refs) returns (bool); 
              /**
              Checks whether the TvmSlice contains the specified amount of data bits and references.
              */              
              function hasNBitsAndRefs(uint16 bits, uint8 refs) returns (bool);
              /**
              Lexicographically compares the slice and other data bits of the root slices and returns result as an integer:
              
              1 - slice > other
              0 - slice == other
              -1 - slice < other
              */              
              function compare(TvmSlice other) returns (int8);
              /**
              Sequentially decodes values of the specified types from the TvmSlice. Supported types: uintN, intN, bytesN, bool, ufixedMxN, fixedMxN, address, contract, TvmCell, bytes, string, mapping, ExtraCurrencyCollection, array, optional and struct. Example:
              
              TvmSlice slice = ...;
              (uint8 a, uint16 b) = slice.decode(uint8, uint16);
              (uint16 num0, uint32 num1, address addr) = slice.decode(uint16, uint32, address);
              */              
              function decode(TypeA a, TypeB b) returns (TypeA /*a*/, TypeB /*b*/);
              /**
              Sequentially decodes values of the specified types from the TvmSlice if the TvmSlice holds sufficient data for all specified types. Otherwise, returns null.
              
              Supported types: uintN, intN, bytesN, bool, ufixedMxN, fixedMxN, address, contract, TvmCell, bytes, string, mapping, ExtraCurrencyCollection, and array.
              
              TvmSlice slice = ...;
              optional(uint) a = slice.decodeQ(uint);
              optional(uint8, uint16) b = slice.decodeQ(uint8, uint16);
              */              
              function decodeQ(TypeA a, TypeB b) returns (optional(TypeA, TypeB)); 
              /**
              Loads a cell from the TvmSlice reference.
              */              
              function loadRef() returns (TvmCell);
              /**
              Loads a cell from the TvmSlice reference and converts it into a TvmSlice.
              */              
              function loadRefAsSlice() returns (TvmSlice);
              /**
              Loads a signed integer with the given bitSize from the TvmSlice.
              */              
              function loadSigned(uint16 bitSize) returns (int); 
              /**
              Loads an unsigned integer with the given bitSize from the TvmSlice.
              */              
              function loadUnsigned(uint16 bitSize) returns (uint); 
              /**
              Loads (deserializes) VarUInteger 16 and returns an unsigned 128-bit integer. See TL-B scheme.
              */              
              function loadTons() returns (uint128); 
              /**
              Loads the first length bits and refs references from the TvmSlice into a separate TvmSlice.
              */              
              function loadSlice(uint length) returns (TvmSlice);
              /**
              Loads the first length bits and refs references from the TvmSlice into a separate TvmSlice.
              */              
              function loadSlice(uint length, uint refs) returns (TvmSlice);
              /**
              Decodes parameters of the function or constructor (if contract type is provided). This function is usually used in onBounce function.
              */              
              function decodeFunctionParams(FunctionOrContractName) returns (TypeA /*a*/, TypeB /*b*/);
              /**
              Decode state variables from slice that is obtained from the field data of stateInit
              
              Example:
              
              contract A {
              	uint a = 111;
              	uint b = 22;
              	uint c = 3;
              	uint d = 44;
              	address e = address(12);
              	address f;
              }
              
              contract B {
              	function f(TvmCell data) public pure {
              		TvmSlice s = data.toSlice();
              		(uint256 pubkey, uint64 timestamp, bool flag,
              			uint a, uint b, uint c, uint d, address e, address f) = s.decodeStateVars(A);
              			
              		// pubkey - pubkey of the contract A
              		// timestamp - timestamp that used for replay protection
              		// flag - always equals to true
              		// a == 111
              		// b == 22
              		// c == 3
              		// d == 44
              		// e == address(12)
              		// f == address(0)
              		// s.empty()
              	}
              }
              */              
              function decodeStateVars(ContractName) returns (uint256 /*pubkey*/, uint64 /*timestamp*/, bool /*constructorFlag*/, Type1 /*var1*/, Type2 /*var2*/); 
              /**
              Skips the first length bits and refs references from the TvmSlice.
              */              
              function skip(uint length);
              /**
              Skips the first length bits and refs references from the TvmSlice.
              */              
              function skip(uint length, uint refs);
          }
        """)
  }

  val tvmBuilder: SolContractDefinition by lazy {
    psiFactory.createContract("""
          contract TvmBuilder {
							/**
Converts a TvmBuilder into TvmSlice.
							*/
							function toSlice() returns (TvmSlice);
							/**
Converts a TvmBuilder into TvmCell.
							*/
							function toCell() returns (TvmCell);
							/**
Returns the number of data bits and references already stored in the TvmBuilder.
							*/
							function size() returns (uint16 /*bits*/, uint8 /*refs*/); 
							/**
Returns the number of data bits already stored in the TvmBuilder.
							*/
							function bits() returns (uint16); 
							/**
Returns the number of references already stored in the TvmBuilder.
							*/
							function refs() returns (uint8); 
							/**
Returns the number of data bits that can still be stored in the TvmBuilder.
							*/
							function remBits() returns (uint16); 
							/**
Returns the number of references that can still be stored in the TvmBuilder.
							*/
							function remRefs() returns (uint8); 
							/**
Returns the number of data bits and references that can still be stored in the TvmBuilder.
							*/
							function remBitsAndRefs() returns (uint16 /*bits*/, uint8 /*refs*/); 
							/**
Returns the depth of TvmBuilder. If no cell references are stored in the builder, then 0 is returned; otherwise function result is one plus the maximum of depths of cells referred to from the builder.
							*/
							function depth() returns (uint16); 
							/**
Stores the list of values into the TvmBuilder.

Internal representation of the stored data:

uintN/intN/bytesN - stored as an N-bit string. For example, uint8(100), int16(-3), bytes2(0xaabb) stored as 0x64fffdaabb.
bool - stored as a binary zero for false or a binary one for true. For example, true, false, true stored as 0xb_.
ufixedMxN/fixedMxN - stored as an M-bit string.
address/contract - stored according to the TL-B scheme of MsgAddress.
TvmCell/bytes/string - stored as a cell in reference.
TvmSlice/TvmBuilder - all data bits and references of the TvmSlice or the TvmBuilder are appended to the TvmBuilder, not in a reference as TvmCell. To store TvmSlice/TvmBuilder in the references use <TvmBuilder>.storeRef().
mapping/ExtraCurrencyCollection - stored according to the TL-B scheme of HashmapE: if map is empty then stored as a binary zero, otherwise as a binary one and the dictionary Hashmap in a reference.
array - stored as a 32 bit value - size of the array and a HashmapE that contains all values of the array.
optional - stored as a binary zero if the optional doesn't contain value. Otherwise, stored as a binary one and the cell with serialized value in a reference.
struct - stored in the order of its members in the builder. Make sure the entire struct fits into the builder.
Note: there is no gap or offset between two consecutive data assets stored in the TvmBuilder.

See TVM to read about notation for bit strings.

Example:

uint8 a = 11;
int16 b = 22;
TvmBuilder builder;
builder.store(a, b, uint(33));
							*/
							function store(/*list_of_values*/); 
							/**
Stores n binary ones into the TvmBuilder.
							*/
							function storeOnes(uint n); 
							/**
Stores n binary zeroes into the TvmBuilder.
							*/
							function storeZeroes(uint n); 
							/**
Stores a signed integer value with given bitSize in the TvmBuilder.
							*/
							function storeSigned(int256 value, uint16 bitSize); 
							/**
Stores an unsigned integer value with given bitSize in the TvmBuilder.
							*/
							function storeUnsigned(uint256 value, uint16 bitSize); 
							/**
Stores TvmBuilder b/TvmCell c/TvmSlice s in the reference of the TvmBuilder.
							*/
							function storeRef(TvmBuilder b); 
							/**
Stores TvmBuilder b/TvmCell c/TvmSlice s in the reference of the TvmBuilder.
							*/
							function storeRef(TvmCell c); 
							/**
Stores TvmBuilder b/TvmCell c/TvmSlice s in the reference of the TvmBuilder.
							*/
							function storeRef(TvmSlice s); 
							/**
Stores (serializes) an integer value and stores it in the TvmBuilder as VarUInteger 16. See TL-B scheme.

See example of how to work with TVM specific types:

Message_construction
Message_parsing
							*/
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
          
							/**
Returns public key that is used to check the message signature. If the message isn't signed then it's equal to 0. See also: Contract execution, pragma AbiHeader.
							*/
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
      
							/**
		Sends an internal outbound message to the address. Function parameters:
		
<ul >
<li><code>value</code> (<code>uint128</code>) - amount of nanotons sent attached to the message. Note: the sent value is
withdrawn from the contract's balance even if the contract has been called by internal inbound message.</li>
<li><code>currencies</code> (<code>ExtraCurrencyCollection</code>) - additional currencies attached to the message. Defaults to
an empty set.</li>
<li><code>bounce</code> (<code>bool</code>) - if it's set and transaction (generated by the internal outbound message) falls
(only at the computing phase, not at the action phase!) then funds will be returned. Otherwise, (flag isn't
set or transaction terminated successfully) the address accepts the funds even if the account
doesn't exist or is frozen. Defaults to <code>true</code>.</li>
<li><code>flag</code> (<code>uint16</code>) - flag that used to send the internal outbound message. Defaults to <code>0</code>.</li>
<li><code>body</code> (<code>TvmCell</code>) -  body (payload) attached to the internal message. Defaults to an empty
TvmCell.</li>
<li><code>stateInit</code> (<code>TvmCell</code>) - represents field <code>init</code> of <code>Message X</code>. If <code>stateInit</code> has a wrong
format, a cell underflow <a href="#tvm-exception-codes">exception</a> at the computing phase is thrown.
See <a href="https://github.com/ton-blockchain/ton/blob/master/crypto/block/block.tlb#L148">here</a>.
Normally, <code>stateInit</code> is used in 2 cases: to deploy the contract or to unfreeze the contract.</li>
</ul>		All parameters can be omitted, except value.

		
		Possible values of parameter flag:
		
		0 - message carries funds equal to the value parameter. Forward fee is subtracted from the value.
		128 - message carries all the remaining balance of the current smart contract. Parameter value is ignored. The contract's balance will be equal to zero after the message processing.
		64 - carries funds equal to the value parameter plus all the remaining value of the inbound message (that initiated the contract execution).
		Parameter flag can also be modified:
		
		flag + 1 - means that the sender wants to pay transfer fees separately from contract's balance.
		flag + 2 - means that any errors arising while processing this message during the action phase should be ignored. But if the message has wrong format, then the transaction fails and + 2 has no effect.
		flag + 32 - means that the current account must be destroyed if its resulting balance is zero. For example, flag: 128 + 32 is used to send all balance and destroy the contract.
		In order to clarify flags usage see this sample.
		
		address dest = ...;
		uint128 value = ...;
		bool bounce = ...;
		uint16 flag = ...;
		TvmCell body = ...;
		ExtraCurrencyCollection c = ...;
		TvmCell stateInit = ...;
		// sequential order of parameters
		addr.transfer(value);
		addr.transfer(value, bounce);
		addr.transfer(value, bounce, flag);
		addr.transfer(value, bounce, flag, body);
		addr.transfer(value, bounce, flag, body, c);
		// using named parameters
		destination.transfer({value: 1 ton, bounce: false, flag: 128, body: cell, currencies: c});
		destination.transfer({bounce: false, value: 1 ton, flag: 128, body: cell});
		destination.transfer({value: 1 ton, bounce: false, stateInit: stateInit});
		See example of address.transfer() usage:
		
		giver
*/
              function transfer(uint128 value, bool bounce, uint16 flag, TvmCell body, ExtraCurrencyCollection currencies, TvmCell stateInit);
							/**

							*/
							function send(uint value) returns (bool);
							/**      
Constructs an address of type addr_std with given workchain id wid and value address_value.
							*/
							function makeAddrStd(int8 wid, uint _address) returns (address);
							/**
Constructs an address of type addr_none.
							*/
							function makeAddrNone() returns (address);
							/**
Constructs an address of type addr_extern with given value with bitCnt bit length.
							*/
							function makeAddrExtern() returns (address);
							/**
Returns type of the address: 0 - addr_none 1 - addr_extern 2 - addr_std
							*/
							function getType() returns (uint8);
							/**
Returns the result of comparison between this address with zero address of type addr_std.
							*/
							function isStdZero() returns (bool);
							/**
Checks whether this address is of type addr_std without any cast.
							*/
							function isStdAddrWithoutAnyCast() returns (bool);
							/**
Returns the result of comparison between this address with zero address of type addr_extern.
							*/
							function isExternZero() returns (bool);
							/**
Checks whether this address is of type addr_none.
							*/
							function isNone() returns (bool);
							/**
Parses address containing a valid MsgAddressInt (addr_std), applies rewriting from the anycast (if present) to the same-length prefix of the address, and returns both the workchain wid and the 256-bit address value. If the address value is not 256-bit, or if address is not a valid serialization of MsgAddressInt, throws a cell deserialization exception.

It's wrapper for opcode REWRITESTDADDR.

Example:

(int8 wid, uint addr) = address(this).unpack();
							*/
							function unpack() returns (int8 /*wid*/, uint256 /*value*/);
      }
    """), true)

  }

  val mappingType: SolContract by lazy {
    SolContract(
      psiFactory.createContract(
        """
      contract ${internalise("Mapping")} {
							/**
Returns the item of ValueType with index key. Throws an exception if key is not in the mapping.
							*/
							function at(KeyType index) returns (ValueType);
							/**
Computes the minimal key in the mapping and returns an optional value containing that key and the associated value. If mapping is empty, this function returns an empty optional.
							*/
							function min() returns (optional(KeyType, ValueType));
							/**
Computes the maximal key in the mapping and returns an optional value containing that key and the associated value. If mapping is empty, this function returns an empty optional.
							*/
							function max() returns (optional(KeyType, ValueType));
							/**
Computes the minimal (maximal) key in the mapping that is lexicographically greater (less) than key and returns an optional value containing that key and the associated value. Returns an empty optional if there is no such key. If KeyType is an integer type, argument for this functions can not possibly fit KeyType.

Example:

KeyType key;
// init key
optional(KeyType, ValueType) nextPair = map.next(key);
optional(KeyType, ValueType) prevPair = map.prev(key);

if (nextPair.hasValue()) {
    (KeyType nextKey, ValueType nextValue) = nextPair.get(); // unpack optional value
    // using nextKey and nextValue
}

mapping(uint8 => uint) m;
optional(uint8, uint) = m.next(-1); // ok, param for next/prev can be negative 
optional(uint8, uint) = m.prev(65537); // ok, param for next/prev can not possibly fit to KeyType (uint8 in this case)
							*/
							function next(KeyType key) returns (optional(KeyType, ValueType));
							/**
Computes the minimal (maximal) key in the mapping that is lexicographically greater (less) than key and returns an optional value containing that key and the associated value. Returns an empty optional if there is no such key. If KeyType is an integer type, argument for this functions can not possibly fit KeyType.

Example:

KeyType key;
// init key
optional(KeyType, ValueType) nextPair = map.next(key);
optional(KeyType, ValueType) prevPair = map.prev(key);

if (nextPair.hasValue()) {
    (KeyType nextKey, ValueType nextValue) = nextPair.get(); // unpack optional value
    // using nextKey and nextValue
}

mapping(uint8 => uint) m;
optional(uint8, uint) = m.next(-1); // ok, param for next/prev can be negative 
optional(uint8, uint) = m.prev(65537); // ok, param for next/prev can not possibly fit to KeyType (uint8 in this case)
							*/
							function prev(KeyType key) returns (optional(KeyType, ValueType));
							/**
Computes the minimal (maximal) key in the mapping that is lexicographically greater than or equal to (less than or equal to) key and returns an optional value containing that key and the associated value. Returns an empty optional if there is no such key. If KeyType is an integer type, argument for this functions can not possibly fit KeyType.
							*/
							function nextOrEq(KeyType key) returns (optional(KeyType, ValueType));
							/**
Computes the minimal (maximal) key in the mapping that is lexicographically greater than or equal to (less than or equal to) key and returns an optional value containing that key and the associated value. Returns an empty optional if there is no such key. If KeyType is an integer type, argument for this functions can not possibly fit KeyType.
							*/
							function prevOrEq(KeyType key) returns (optional(KeyType, ValueType));
							/**
If mapping is not empty then this function computes the minimal (maximum) key of the mapping, deletes that key and the associated value from the mapping and returns an optional value containing that key and the associated value. Returns an empty optional if there is no such key.							*/
							function delMin() returns (optional(KeyType, ValueType));
							/**
If mapping is not empty then this function computes the minimal (maximum) key of the mapping, deletes that key and the associated value from the mapping and returns an optional value containing that key and the associated value. Returns an empty optional if there is no such key.							*/
							function delMax() returns (optional(KeyType, ValueType));
							/**
Checks whether key is present in the mapping and returns an optional with the associated value. Returns an empty optional if there is no such key.
							*/
							function fetch(KeyType key) returns (optional(ValueType));
							/**
Returns whether key is present in the mapping.
							*/
							function exists(KeyType key) returns (bool);
							/**
Returns whether the mapping is empty.
							*/
							function empty() returns (bool);
							/**
Sets the value associated with key only if key is present in the mapping and returns the success flag.
							*/
							function replace(KeyType key, ValueType value) returns (bool);
							/**
Sets the value associated with key only if key is not present in the mapping.
							*/
							function add(KeyType key, ValueType value) returns (bool);
							/**
Sets the value associated with key, but also returns an optional with the previous value associated with the key, if any. Otherwise, returns an empty optional.
							*/
							function getSet(KeyType key, ValueType value) returns (optional(ValueType));
							/**
Sets the value associated with key, but only if key is not present in the mapping. Returns an optional with the old value without changing the dictionary if that value is present in the mapping, otherwise returns an empty optional.
							*/
							function getAdd(KeyType key, ValueType value) returns (optional(ValueType));
							/**
Sets the value associated with key, but only if key is present in the mapping. On success, returns an optional with the old value associated with the key. Otherwise, returns an empty optional.
							*/
							function getReplace(KeyType key, ValueType value) returns (optional(ValueType));
							/**
 Returns all mapping's keys/values. Note: these functions iterate over the whole mapping, thus the cost is proportional to the mapping's size.
							*/
							function keys() returns (KeyType[]);
							/**
 Returns all values of the mapping as an array. Note: these functions iterate over the whole mapping, thus the cost is proportional to the mapping's size.
							*/
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

							/**
Checks whether the optional contains a value.
							*/
							function hasValue() returns (bool);
							/**
Returns the contained value, if the optional contains one. Otherwise, throws an exception.
							*/
							function get() returns (Type);
							/**
Replaces content of the optional with value.
							*/
							function set(Type value);
							/**
Deletes content of the optional.
							*/
							function reset();
      }
    """), true)
  }

  val abiType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Abi")} {
          // todo varargs
							/**
creates cell from the values.
							*/
							function encode(TypeA a) returns (TvmCell /*cell*/);
							/**
decodes the cell and returns the values.
							*/
							function decode(TvmCell cell) returns (TypeA);
      }
    """), true)
  }

  val structType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Struct")} {
							/**
							*/
							function unpack() returns (TypeA /*a*/, TypeB /*b*/);
      }
    """), true)
  }

  val stringType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("String")} {
							/**
							*/
							function empty() returns (bool);
							/**
							*/
							function byteLength() returns (uint32);
							/**
							*/
							function substr(uint from) returns (string);
							/**
							*/
							function substr(uint from, uint count) returns (string); 
							/**
							*/
							function append(string tail);
							/**
							*/
							function find(bytes1 symbol) returns (optional(uint32));
							/**
							*/
							function find(string substr) returns (optional(uint32));
							/**
							*/
							function findLast(bytes1 symbol) returns (optional(uint32));
							/**
							*/
							function toUpperCase() returns (string)
							/**
							*/
							function toLowerCase() returns (string)
							/**
							*/
							function format(string template, TypeA a, TypeB b, ...) returns (string);
							/**
							*/
							function stoi(string inputStr) returns (optional(int) /*result*/);
        }
    """), true)
  }

  val rndType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Rnd")} {
							/**
							*/
							function next(Type/*[Type limit]*/) returns (Type); 
							/**
							*/
							function next() returns (uint256); 
							/**
							*/
							function getSeed() returns (uint256);
							/**
							*/
							function setSeed(uint256 x);
							/**
							*/
							function shuffle(uint someNumber);
							/**
							*/
							function shuffle();
        }
    """), true)
  }


  val bytesType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Bytes")} {
							/**
							*/
							function empty() returns (bool);
							/**
							*/
//							function operator[](uint index) returns (byte);
							/**
							*/
//							function operator[](uint from, uint to) returns (bytes);
							/**
							*/
							function length() returns (uint) 
							/**
							*/
							function toSlice() returns (TvmSlice);
							/**
							*/
							function dataSize(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
							/**
							*/
							function dataSizeQ(uint n) returns (optional(uint /*cells*/, uint /*bits*/, uint /*refs*/));
							/**
							*/
							function append(bytes tail);
      }
    """), true)
  }

  val tvmType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Tvm")} {
          // todo varargs
							/**
Executes TVM instruction "ACCEPT" (TVM - A.11.2). This instruction sets current gas limit to its maximal allowed value. This action is required to process external messages that bring no value.
							*/
							function accept();
							/**
Executes TVM instruction "SETGASLIMIT" (TVM - A.11.2). Sets current gas limit gl to the minimum of g and gm, and resets the gas credit gc to zero. If the gas consumed so far (including the present instruction) exceeds the resulting value of gl, an (unhandled) out of gas exception is thrown before setting new gas limits. Notice that tvm.setGasLimit(...) with an argument g ≥ 263 - 1 is equivalent to tvm.accept(). tvm.setGasLimit() is similar to tvm.accept(). tvm.accept() sets gas limit gl to the maximum possible value (depends on the network configuration parameters, usually is equal to 1_000_000 units of gas). tvm.setGasLimit() is generally used for accepting external messages and restricting max possible gas consumption. It may be used to protect from flood by "bad" owner in a contract that is used by multiple users. Let's consider some scenario:

Check whether msg.pubkey() != 0 and msg.pubkey() belongs to the list of trusted public keys;
Check whether m_floodCounter[msg.pubkey()] < 5 where m_floodCounter is count of pending operations of msg.pubkey() user.
tvm.setGasLimit(75_000); accept external message and set gas limit to 75_000.
++m_floodCounter[msg.pubkey()]; increase count of pending operations for current users.
tvm.commit(); save current state if it needs
Do other things.
So if some user's public key will be stolen, then a hacker can spam with external messages and burn at most 5 * 75_000 units of gas instead of 5 * 1_000_000, because we use tvm.setGasLimit() instead of tvm.accept().							*/
							function setGasLimit(uint g);
							/**
Computes the amount of gas that can be bought for value nanotons, and sets gl
accordingly in the same way as tvm.setGasLimit().
							*/
							function buyGas(uint value);
							/**
Creates a "check point" of the state variables (by copying them from c7 to c4) and register c5. If the contract throws an exception at the computing phase then the state variables and register c5 will roll back to the "check point", and the computing phase will be considered "successful". If contract doesn't throw an exception, it has no effect.
							*/
							function commit();
							/**
Same as tvm.commit() but doesn't copy the state variables from c7 to c4. It's a wrapper for opcode COMMIT. See TVM.

Note: Don't use tvm.rawCommit() after tvm.accept() in processing external messages because you don't save from c7 to c4 the hidden state variable timestamp that is used for replay protection.
							*/
							function rawCommit();
							/**
Note: Function is experimental.

A dual of the tvm.setData()function. It returns value of the c4 register. Obtaining a raw storage cell can be useful when upgrading a new version of the contract that introduces an altered data layout.

Manipulation with a raw storage cell requires understanding of the way the compiler stores the data. Refer to the description of tvm.setData() below to get more details.

Note: state variables and replay protection timestamp stored in the data cell have the same values that were before the transaction. See tvm.commit() to learn about register c4 update.
							*/
							function getData() returns (TvmCell);
							/**
Note: Function is experimental.

Stores cell data in the register c4. Mind that after returning from a public function all state variables from c7 are copied to c4 and tvm.setData will have no effect. Example hint, how to set c4:

TvmCell data = ...;
tvm.setData(data); // set register c4
tvm.rawCommit();   // save register c4 and c5
revert(200);       // throw the exception to terminate the transaction
Be careful with the hidden state variable timestamp and think about possibility of external messages replaying.
							*/
							function setData(TvmCell data);
							/**
Dumps log string. This function is a wrapper for TVM instructions PRINTSTR (for constant literal strings shorter than 16 symbols) and STRDUMP (for other strings). logtvm is an alias for tvm.log(string). Example:

tvm.log("Hello, world!");
logtvm("99_Bottles");

string s = "Some_text";
tvm.log(s);
Note: For long strings dumps only the first 127 symbols.
							*/
							function log(string log);
							/**
Dumps cell data or integer. Note that for cells this function dumps data only from the first cell. T must be an integer type or TvmCell.

Example:

TvmBuilder b;
b.storeUnsigned(0x9876543210, 40);
TvmCell c = b.toCell();
tvm.hexdump(c);
tvm.bindump(c);
uint a = 123;
tvm.hexdump(a);
tvm.bindump(a);
int b = -333;
tvm.hexdump(b);
tvm.bindump(b);
Expected output for the example:

CS<9876543210>(0..40)
CS<10011000011101100101010000110010000100001>(0..40)
7B
1111011
-14D
-101001101
							*/
							function hexdump(T a);
							/**
Dumps cell data or integer. Note that for cells this function dumps data only from the first cell. T must be an integer type or TvmCell.

Example:

TvmBuilder b;
b.storeUnsigned(0x9876543210, 40);
TvmCell c = b.toCell();
tvm.hexdump(c);
tvm.bindump(c);
uint a = 123;
tvm.hexdump(a);
tvm.bindump(a);
int b = -333;
tvm.hexdump(b);
tvm.bindump(b);
Expected output for the example:

CS<9876543210>(0..40)
CS<10011000011101100101010000110010000100001>(0..40)
7B
1111011
-14D
-101001101
							*/
							function bindump(T a);
							/**
This command creates an output action that would change this smart contract code to that given by the TvmCell newCode (this change will take effect only after the successful termination of the current run of the smart contract).

See example of how to use this function:

old contract
new contract
							*/
							function setcode(TvmCell newCode);
							/**
							*/
							function configParam(uint8 paramNumber) returns (TypeA a, TypeB b);
							/**
							*/
							function rawConfigParam(uint8 paramNumber) returns (TvmCell cell, bool status); 
							/**
							*/
							function rawReserve(uint value, uint8 flag);
							/**
							*/
							function rawReserve(uint value, ExtraCurrencyCollection currency, uint8 flag);
							/**
							*/
							function initCodeHash() returns (uint256 hash); 
							/**
							*/
							function hash(TvmCell cellTree) returns (uint256); 
							/**
							*/
							function hash(string data) returns (uint256); 
							/**
							*/
							function hash(bytes data) returns (uint256); 
							/**
							*/
							function hash(TvmSlice data) returns (uint256); 
							/**
							*/
							function checkSign(uint256 hash, uint256 SignHighPart, uint256 SignLowPart, uint256 pubkey) returns (bool); 
							/**
							*/
							function checkSign(uint256 hash, TvmSlice signature, uint256 pubkey) returns (bool); 
							/**
							*/
							function checkSign(TvmSlice data, TvmSlice signature, uint256 pubkey) returns (bool); 
							/**
							*/
							function insertPubkey(TvmCell stateInit, uint256 pubkey) returns (TvmCell); 
							/**
							*/
							function buildStateInit(TvmCell code, TvmCell data) returns (TvmCell stateInit); 
							/**
							*/
							function buildStateInit(TvmCell code, TvmCell data, uint8 splitDepth) returns (TvmCell stateInit);
							/**
							*/
							function buildStateInit(TvmCell code, TvmCell data, uint8 splitDepth, uint256 pubkey, Contract contr, VarInit varInit);
							/**
							*/
							function stateInitHash(uint256 codeHash, uint256 dataHash, uint16 codeDepth, uint16 dataDepth) returns (uint256);
							/**
							*/
							function code() returns (TvmCell);
							/**
							*/
							function codeSalt(TvmCell code) returns (optional(TvmCell) optSalt);
							/**
							*/
							function setCodeSalt(TvmCell code, TvmCell salt) returns (TvmCell newCode); 
							/**
							*/
							function pubkey() returns (uint256);
							/**
							*/
							function setPubkey(uint256 newPubkey); 
							/**
							*/
							function setCurrentCode(TvmCell newCode); 
							/**
							*/
							function resetStorage();
							/**
							*/
							function functionId(Function functionName) returns (uint32);
							/**
							*/
							function functionId(Contract ContractName) returns (uint32); 
							/**
							*/
							function encodeBody(functionName, arg0, arg1, arg2) returns (TvmCell);
							/**
							*/
							function encodeBody(functionName, callbackFunction, arg0, arg1, arg2) returns (TvmCell);
							/**
							*/
							function encodeBody(contractName, arg0, arg1, arg2) returns (TvmCell);
							/**
							*/
							function exit();
							/**
							*/
							function exit1();
							/**
							*/
							function buildDataInit(uint256 pubkey, contractName, VarInit varInit);
							/**
							*/
							function buildExtMsg(
               address dest,
               uint64 time,
               uint32 expire,
							/**
							*/
							functionIdentifier /*[, list of function arguments]}*/ call,
               bool sign,
               optional(uint256) pubkey,
               uint32 /*| functionIdentifier*/ callbackId,
               uint32 /*| functionIdentifier*/ onErrorId,
               TvmCell stateInit,
               optional(uint32) signBoxHandle,
               uint8 abiVer,
               uint8 flags
           ) returns (TvmCell);
							/**
							*/
							function buildIntMsg(
               address dest,
               uint128 value,
							/**
							*/
							functionName/*, [callbackFunction,] arg0, arg1, arg2, ...}*/ call,
               bool bounce,
               ExtraCurrencyCollection currencies, 
               TvmCell stateInit 
           )
           returns (TvmCell);
							/**
							*/
							function sendrawmsg(TvmCell msg, uint8 flag);
           
      }
    """), true)
  }


  val mathType: SolContract by lazy {
    SolContract(psiFactory.createContract("""
      contract ${internalise("Abi")} {
           // todo varargs
							/**
							*/
							function min(T a, T b) returns (T);
							/**
							*/
							function max(T a, T b) returns (T);
							/**
							*/
							function minmax(T a, T b) returns (T /*min*/, T /*max*/);
							/**
							*/
							function abs(intM val) returns (intM);
							/**
							*/
							function abs(fixedMxN val) returns (fixedMxN);
							/**
							*/
							function modpow2(uint value, uint power) returns (uint);
							/**
							*/
							function divc(T a, T b) returns (T);
							/**
							*/
							function divr(T a, T b) returns (T);
							/**
							*/
							function muldivmod(T a, T b, T c) returns (T /*result*/, T /*remainder*/);
							/**
							*/
							function muldiv(T a, T b, T c) returns (T);
							/**
							*/
							function muldivr(T a, T b, T c) returns (T);
							/**
							*/
							function muldivc(T a, T b, T c) returns (T);
							/**
							*/
							function divmod(T a, T b) returns (T /*result*/, T /*remainder*/);
							/**
							*/
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
          $rndType rnd;
          
          uint now;

							/**
							*/
              function assert(bool condition)  {}
							/**
							*/
              function bitSize(int x) private returns (uint16)   {}
							/**
							*/
              function uBitSize(uint x) returns (uint16)   {}
							/**
							*/
							function require(bool condition)  {}
							/**
							*/
							function require(bool condition, string message)  {}
							/**
							*/
							function revert()  {}
							/**
							*/
							function revert(string) {}
							/**
							*/
							function keccak256()  returns (bytes32) {} 
							/**
							*/
							function sha3()  returns (bytes32)  {}
							/**
							*/
							function sha256()  returns (bytes32)  {}
							/**
							*/
							function ripemd160()  returns (bytes20)  {}
							/**
							*/
							function ecrecover(bytes32 hash, uint8 v, bytes32 r, bytes32 s)  returns (address)  {}
							/**
							*/
							function addmod(uint x, uint y, uint k)  returns (uint)  {}
							/**
							*/
							function mulmod(uint x, uint y, uint k)  returns (uint) {}
							/**
							*/
							function selfdestruct(address recipient)  {};
          
          logtvm(string log);
      }
    """), true)
  }
}
