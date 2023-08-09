package com.broxus.solidity.lang.types

import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.lang.psi.SolNamedElement
import com.broxus.solidity.lang.psi.SolPsiFactory
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language

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
      vectorType,
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
              Returns the depth of <code>TvmSlice</code>. If the <code>TvmSlice</code> has no references, then 0 is returned, otherwise function result is one plus the maximum of depths of the cells referred to from the slice.
              */
              function depth() returns(uint16); 
              /**
              Returns the number of distinct cells, data bits in the distinct cells and cell references in the distinct cells. If number of the distinct cells exceeds <code>n+1</code> then a cell overflow <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvm-exception-codes">exception</a> is thrown. This function is a wrapper for the <code>CDATASIZE</code> opcode (<a href="">TVM</a> - A.11.7).
              */              
              function dataSize(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
              /**
              Returns the number of distinct cells, data bits in the distinct cells and cell references in the distinct cells. If number of the distinct cells exceeds <code>n+1</code> then this function returns an <code>optional</code> that has no value. This function is a wrapper for the <code>CDATASIZEQ</code> opcode (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.7).
              */              
              function dataSizeQ(uint n) returns (optional(uint /*cells*/, uint /*bits*/, uint /*refs*/)); 
              /**
              Converts a <code>TvmCell</code> to <code>TvmSlice</code>.
              */              
              function toSlice() returns (TvmSlice); 
          }
        """)
  }

  val tvmSlice: SolContractDefinition by lazy {
    psiFactory.createContract("""
          contract TvmSlice {
              /**
              Checks whether the <code>TvmSlice</code> is empty (i.e., contains no data bits and no cell references).
              */              
              function empty() returns (bool);
              /**
              Returns the number of data bits and references in the <code>TvmSlice</code>.
              */  
              function size() returns (uint16 /*bits*/, uint8 /*refs*/);
              /**
              Returns the number of data bits in the <code>TvmSlice</code>.
              */              
              function bits() returns (uint16);
              /**
              Returns the number of references in the <code>TvmSlice</code>.
              */              
              function refs() returns (uint8);
              /**
              Returns the number of distinct cells, data bits in the distinct cells and cell references in the distinct cells. If number of the distinct cells exceeds <code>n+1</code> then a cell overflow <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvm-exception-codes">exception</a> is thrown. Note that the returned <code>count of distinct cells</code> does not take into account the cell that contains the slice itself. This function is a wrapper for <code>SDATASIZE</code> opcode (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.7).
              */              
              function dataSize(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
              /**
              Returns the number of distinct cells, data bits in the distinct cells and cell references in the distinct cells. If number of the distinct cells exceeds <code>n+1</code> then this function returns an <code>optional</code> that has no value. Note that the returned count of distinct cells does not take into account the cell that contains the slice itself. This function is a wrapper for <code>SDATASIZEQ</code> opcode (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.7).              */              
              function dataSizeQ(uint n) returns (uint /*cells*/, uint /*bits*/, uint /*refs*/);
              /**
              Returns the depth of <code>TvmSlice</code>. If the <code>TvmSlice</code> has no references, then 0 is returned, otherwise function result is one plus the maximum of depths of the cells referred to from the slice.              
              */  
              function depth() returns (uint16);
              /**
              Checks whether the <code>TvmSlice</code> contains the specified amount of data bits and references.
              */              
              function hasNBits(uint16 bits) returns (bool); 
              /**
              Checks whether the <code>TvmSlice</code> contains the specified amount of data bits and references.
              */              
              function hasNRefs(uint8 refs) returns (bool); 
              /**
              Checks whether the <code>TvmSlice</code> contains the specified amount of data bits and references.
              */              
              function hasNBitsAndRefs(uint16 bits, uint8 refs) returns (bool);
              /**
              Lexicographically compares the <code>slice</code> and <code>other</code> data bits of the root slices and returns result as an integer:
              
              <li>1 - <code>slice</code> > <code>other</code></li>
              <li>0 - <code>slice</code> == <code>other</code></li>
              <li>-1 - <code>slice</code> < <code>other</li>
              */        
              function compare(TvmSlice other) returns (int8);
              /**
              Sequentially decodes values of the specified types from the <code>TvmSlice</code>. Supported types: <code>uintN</code>, <code>intN</code>, <code>bytesN</code>, <code>bool</code>, <code>ufixedMxN</code>, <code>fixedMxN</code>, <code>address</code>, <code>contract</code>, <code>TvmCell</code>, <code>bytes</code>, <code>string</code>, <code>mapping</code>, <code>ExtraCurrencyCollection</code>, <code>array</code>, <code>optional</code> and <code>struct</code>. Example:
              
              <code><pre>TvmSlice slice = ...;
              (uint8 a, uint16 b) = slice.decode(uint8, uint16);
              (uint16 num0, uint32 num1, address addr) = slice.decode(uint16, uint32, address);</pre></code>
               @custom:no_validation
               @custom:typeArgument Type:Type
              */              
              function decode(Type varargs) returns (Type);
              /**
              Sequentially decodes values of the specified types from the <code>TvmSlice</code> if the <code>TvmSlice</code> holds sufficient data for all specified types. Otherwise, returns null.
              
              Supported types: <code>uintN</code>, <code>intN</code>, <code>bytesN</code>, <code>bool</code>, <code>ufixedMxN</code>, <code>fixedMxN</code>, <code>address</code>, <code>contract</code>, <code>TvmCell</code>, <code>bytes</code>, <code>string</code>, <code>mapping</code>, <code>ExtraCurrencyCollection</code>, and <code>array</code>.
              
              <code><pre>TvmSlice slice = ...;
              optional(uint) a = slice.decodeQ(uint);
              optional(uint8, uint16) b = slice.decodeQ(uint8, uint16);</pre></code>
              @custom:no_validation
              @custom:typeArgument Type:Type
              */              
              function decodeQ(Type varargs) returns (optional(Type)); 
              /**
              Loads a cell from the <code>TvmSlice</code> reference.
              */              
              function loadRef() returns (TvmCell);
              /**
              Loads a cell from the <code>TvmSlice</code> reference and converts it into a <code>TvmSlice</code>.
              */              
              function loadRefAsSlice() returns (TvmSlice);
              /**
              Loads a signed integer with the given bitSize from the <code>TvmSlice</code>.
              */              
              function loadSigned(uint16 bitSize) returns (int); 
              /**
              Loads an unsigned integer with the given bitSize from the <code>TvmSlice</code>.
              */              
              function loadUnsigned(uint16 bitSize) returns (uint); 
              /**
              Loads (deserializes) <strong>VarUInteger 16</strong> and returns an unsigned 128-bit integer. See <a href="https://github.com/ton-blockchain/ton/blob/master/crypto/block/block.tlb">TL-B scheme</a>.
              */              
              function loadTons() returns (uint128); 
              /**
              Loads the first <code>length</code> bits and <code>refs</code> references from the <code>TvmSlice</code> into a separate <code>TvmSlice</code>.
              */              
              function loadSlice(uint length) returns (TvmSlice);
              /**
              Loads the first length bits and refs references from the <code>TvmSlice</code> into a separate <code>TvmSlice</code>.
              */              
              function loadSlice(uint length, uint refs) returns (TvmSlice);
              /**
              Decodes parameters of the function or constructor (if contract type is provided). This function is usually used in <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#onbounce">onBounce</a> function.
              @custom:no_validation
              @custom:typeArgument Type:FunctionOrContract, TypeRet:DecodedElement
              */              
              function decodeFunctionParams(Type functionOrContract) returns (TypeRet);
              /**
              Decode state variables from slice that is obtained from the field data of stateInit
              
              Example:
              
              <code><pre>contract A {
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
              }</pre></code>
              @custom:typeArgument Type:ContractName
              
              */              
              function decodeStateVars(Type contractName) returns (uint256 /*pubkey*/, uint64 /*timestamp*/, bool /*constructorFlag*/, Type1 /*var1*/, Type2 /*var2*/); 
              /**
              Skips the first <code>length</code> bits and <code>refs</code> references from the <code>TvmSlice</code>.
              */              
              function skip(uint length);
              /**
              Skips the first <code>length</code> bits and <code>refs</code> references from the <code>TvmSlice</code>.
              */              
              function skip(uint length, uint refs);
          }
        """)
  }

  val tvmBuilder: SolContractDefinition by lazy {
    psiFactory.createContract("""
          contract TvmBuilder {
							/**
Converts a <code>TvmBuilder</code> into <code>TvmSlice</code>.
							*/
							function toSlice() returns (TvmSlice);
							/**
Converts a <code>TvmBuilder</code> into <code>TvmCell</code>.
							*/
							function toCell() returns (TvmCell);
							/**
Returns the number of data bits and references already stored in the <code>TvmBuilder</code>.
							*/
							function size() returns (uint16 /*bits*/, uint8 /*refs*/); 
							/**
Returns the number of data bits already stored in the <code>TvmBuilder</code>.
							*/
							function bits() returns (uint16); 
							/**
Returns the number of references already stored in the <code>TvmBuilder</code>.
							*/
							function refs() returns (uint8); 
							/**
Returns the number of data bits that can still be stored in the <code>TvmBuilder</code>.
							*/
							function remBits() returns (uint16); 
							/**
Returns the number of references that can still be stored in the <code>TvmBuilder</code>.
							*/
							function remRefs() returns (uint8); 
							/**
Returns the number of data bits and references that can still be stored in the <code>TvmBuilder</code>.
							*/
							function remBitsAndRefs() returns (uint16 /*bits*/, uint8 /*refs*/); 
							/**
Returns the depth of <code>TvmBuilder</code>. If no cell references are stored in the builder, then 0 is returned; otherwise function result is one plus the maximum of depths of cells referred to from the builder.
							*/
							function depth() returns (uint16); 
							/**


Stores the list of values into the <code>TvmBuilder</code>.

Internal representation of the stored data:

<code>uintN</code>/<code>intN</code>/<code>bytesN</code> - stored as an N-bit string. For example, uint8(100), int16(-3), bytes2(0xaabb) stored as 0x64fffdaabb.
<code>bool</code> - stored as a binary zero for false or a binary one for true. For example, true, false, true stored as 0xb_.
<code>ufixedMxN</code>/<code>fixedMxN</code> - stored as an M-bit string.
<code>address</code>/<code>contract</code> - stored according to the TL-B scheme of MsgAddress.
<code>TvmCell</code>/<code>bytes</code>/<code>string</code> - stored as a cell in reference.
<code>TvmSlice</code>/<code>TvmBuilder</code> - all data bits and references of the <code>TvmSlice</code> or the TvmBuilder are appended to the TvmBuilder, not in a reference as <code>TvmCell</code>. To store <code>TvmSlice</code>/<code>TvmBuilder</code> in the references use <TvmBuilder>.storeRef().
<code>mapping</code>/<code>ExtraCurrencyCollection</code> - stored according to the TL-B scheme of HashmapE: if map is empty then stored as a binary zero, otherwise as a binary one and the dictionary Hashmap in a reference.
<code>array</code> - stored as a 32 bit value - size of the array and a HashmapE that contains all values of the array.
<code>optional</code> - stored as a binary zero if the optional doesn't contain value. Otherwise, stored as a binary one and the cell with serialized value in a reference.
<code>struct</code> - stored in the order of its members in the builder. Make sure the entire struct fits into the builder.
Note: there is no gap or offset between two consecutive data assets stored in the TvmBuilder.

See <a href="https://test.ton.org/tvm.pdf">TVM</a> to read about notation for bit strings.

Example:

<code><pre>uint8 a = 11;
int16 b = 22;
TvmBuilder builder;
builder.store(a, b, uint(33));</pre></code> @custom:no_validation
               @custom:typeArgument Type
							*/
							function store(Type varargs); 
							/**
Stores <code>n</code> binary ones into the <code>TvmBuilder</code>.
							*/
							function storeOnes(uint n); 
							/**
Stores <code>n</code> binary zeroes into the <code>TvmBuilder</code>.
							*/
							function storeZeroes(uint n); 
							/**
Stores a signed integer value with given bitSize in the <code>TvmBuilder</code>.
							*/
							function storeSigned(int256 value, uint16 bitSize); 
							/**
Stores an unsigned integer value with given bitSize in the <code>TvmBuilder</code>.
							*/
							function storeUnsigned(uint256 value, uint16 bitSize); 
							/**
Stores <code>TvmBuilder b</code>/<code>TvmCell c</code>/<code>TvmSlice s</code> in the reference of the <code>TvmBuilder</code>.
							*/
							function storeRef(TvmBuilder b); 
							/**
Stores <code>TvmBuilder b</code>/<code>TvmCell c</code>/<code>TvmSlice</code> s in the reference of the <code>TvmBuilder</code>.
							*/
							function storeRef(TvmCell c); 
							/**
Stores <code>TvmBuilder b</code>/<code>TvmCell c</code>/<code>TvmSlice</code> s in the reference of the <code>TvmBuilder</code>.
							*/
							function storeRef(TvmSlice s); 
							/**
Stores (serializes) an integer value and stores it in the <code>TvmBuilder</code> as <strong>VarUInteger 16</strong>. See <a href="https://github.com/ton-blockchain/ton/blob/master/crypto/block/block.tlb">TL-B scheme</a>.

See example of how to work with TVM specific types:

<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/15_MessageSender.sol">Message_construction</a></li>
<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/15_MessageReceiver.sol">Message_parsing</a></li>
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
    contract("""
      contract ${internalise("Msg")} {
          bytes public data;
          uint public gas;
          address public sender;
          uint128 public value;
          
          ExtraCurrencyCollection currencies;
          
          uint32 createdAt;
          
          TvmSlice data;
          
          bool hasStateInit; 
          
							/**
Returns public key that is used to check the message signature. If the message isn't signed then it's equal to <code>0</code>. See also: <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#contract-execution">Contract execution</a>,<a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#pragma-abiheader">pragma AbiHeader</a>.
							*/
							function pubkey() returns (uint256);
          
      }
    """)
  }

  val txType: SolContract by lazy {
    contract("""
      contract ${internalise("Tx")} {
          uint public gasprice;
          address public origin;
          
          uint64 public timestamp;
           
          uint64 public storageFee; 
      }
    """)
  }

  val addressType: SolContract by lazy {
    contract("""
      contract ${internalise("Address")} {
          int8 public wid;
          
          uint public value;
          
          uint128 public balance;
          
          ExtraCurrencyCollection public currencies;
          
          /**
          Sends an internal outbound message to the address, returns false on failure,
          */
          function send(uint value) returns (bool);
      
							/**
		Sends an internal outbound message to the address. Function parameters:
		
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
</ul>		All parameters can be omitted, except <code>value</code>.

		
		Possible values of parameter <code>flag</code>:
		
		<li><code>0</code> - message carries funds equal to the value parameter. Forward fee is subtracted from the value.</li>
		<li><code>128</code> - message carries all the remaining balance of the current smart contract. Parameter value is ignored. The contract's balance will be equal to zero after the message processing.</li>
		<li><code>64</code> - carries funds equal to the value parameter plus all the remaining value of the inbound message (that initiated the contract execution).</li>
		
        Parameter <code>flag</code> can also be modified:
		
		<li><code>flag + 1</code> - means that the sender wants to pay transfer fees separately from contract's balance.</li>
        <li><code>flag + 2</code> - means that any errors arising while processing this message during the action phase should be ignored. But if the message has wrong format, then the transaction fails and + 2 has no effect.</li>
        <li><code>flag + 32</code> - means that the current account must be destroyed if its resulting balance is zero. For example, flag: 128 + 32 is used to send all balance and destroy the contract.</li>
		
        In order to clarify flags usage see <a href="https://github.com/tonlabs/samples/blob/master/solidity/20_bomber.sol">this sample</a>.
		
		<code><pre>address dest = ...;
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
		destination.transfer({value: 1 ton, bounce: false, stateInit: stateInit});</pre></code>
		
        See example of address.transfer() usage:
		
		<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/7_Giver.sol">giver</a></li>
    @custom:no_validation
*/
              function transfer(uint128 value, bool bounce, uint16 flag, TvmCell body, ExtraCurrencyCollection currencies, TvmCell stateInit);
							/**      
Constructs an <code>address</code> of type <strong>addr_std</strong> with given workchain id wid and value <strong>address_value</strong>.
							*/
							function makeAddrStd(int8 wid, uint _address) returns (address);
							/**
Constructs an <code>address</code> of type <strong>addr_none</strong>.
							*/
							function makeAddrNone() returns (address);
							/**
Constructs an <code>address</code> of type <strong>addr_extern</strong> with given value with <strong>bitCnt</strong> bit length.
							*/
							function makeAddrExtern() returns (address);
							/**
Returns type of the <code>addr_none</code>: 0 - <strong>addr_none</strong> 1 - <strong>addr_extern</strong> 2 - <strong>addr_std</strong>
							*/
							function getType() returns (uint8);
							/**
Returns the result of comparison between this <code>address</code> with zero <code>address</code> of type <strong>addr_std</strong>.
							*/
							function isStdZero() returns (bool);
							/**
Checks whether this <code>address</code> is of type <strong>addr_std</strong> without any cast.
							*/
							function isStdAddrWithoutAnyCast() returns (bool);
							/**
Returns the result of comparison between this <code>address</code> with zero <code>address</code> of type <strong>addr_extern</strong>.
							*/
							function isExternZero() returns (bool);
							/**
Checks whether this <code>address</code> is of type <strong>addr_none</strong>.
							*/
							function isNone() returns (bool);
							/**
Parses <code>address</code> containing a valid <code>MsgAddressInt</code> (<code>addr_std</code>), applies rewriting from the anycast (if present) to the same-length prefix of the address, and returns both the workchain <code>wid</code> and the 256-bit address <code>value</code>. If the address <code>value</code> is not 256-bit, or if <code>address</code> is not a valid serialization of <code>MsgAddressInt</code>, throws a cell deserialization <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvm-exception-codes">exception</a> .

It's wrapper for opcode <code>REWRITESTDADDR</code>.

Example:

<code>(int8 wid, uint addr) = address(this).unpack();</code>
 @custom:no_validation
							*/
							function unpack() returns (int8 /*wid*/, uint256 /*value*/);
      }
    """)

  }

  val mappingType: SolContract by lazy {
    contract(
        """
      /**
       @custom:typeArgument KeyType=KeyType,ValueType=ValueType
      */
      contract ${internalise("Mapping")} {
							/**
Returns the item of <code>ValueType</code> with index key. Throws an <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvm-exception-codes">exception</a> if key is not in the mapping.
							*/
							function at(KeyType index) returns (ValueType);
							/**
Computes the minimal key in the <code>mapping</code> and returns an <code>optional</code> value containing that key and the associated value. If <code>mapping</code> is empty, this function returns an empty <code>optional</code>.
							*/
							function min() returns (optional(KeyType, ValueType));
							/**
Computes the maximal key in the <code>mapping</code> and returns an <code>optional</code> value containing that key and the associated value. If <code>mapping</code> is empty, this function returns an empty <code>optional</code>.
							*/
							function max() returns (optional(KeyType, ValueType));
							/**
Computes the minimal (maximal) key in the <code>mapping</code> that is lexicographically greater (less) than key and returns an <code>optional</code> value containing that key and the associated value. Returns an empty <code>optional</code> if there is no such key. If KeyType is an integer type, argument for this functions can not possibly fit <code>KeyType</code>.

Example:

<code><pre>KeyType key;
	// init key
	optional(KeyType, ValueType) nextPair = map.next(key);
	optional(KeyType, ValueType) prevPair = map.prev(key);
	
	if (nextPair.hasValue()) {
		(KeyType nextKey, ValueType nextValue) = nextPair.get(); // unpack optional value
		// using nextKey and nextValue
	}
	
	mapping(uint8 => uint) m;
	optional(uint8, uint) = m.next(-1); // ok, param for next/prev can be negative 
	optional(uint8, uint) = m.prev(65537); // ok, param for next/prev can not possibly fit to KeyType (uint8 in this case)</pre></code>
							*/
							function next(KeyType key) returns (optional(KeyType, ValueType));
							/**
Computes the minimal (maximal) key in the <code>mapping</code> that is lexicographically greater than or equal to (less than or equal to) <strong>key</strong> and returns an <code>optional</code> value containing that key and the associated value. Returns an empty <code>optional</code> if there is no such key. If KeyType is an integer type, argument for this functions can not possibly fit <code>KeyType</code>.
							*/
							function nextOrEq(KeyType key) returns (optional(KeyType, ValueType));
							/**
							Computes the minimal (maximal) key in the <code>mapping</code> that is lexicographically greater than or equal to (less than or equal to) <strong>key</strong> and returns an <code>optional</code> value containing that key and the associated value. Returns an empty <code>optional</code> if there is no such key. If KeyType is an integer type, argument for this functions can not possibly fit <code>KeyType</code>.
							*/
							function prevOrEq(KeyType key) returns (optional(KeyType, ValueType));
							/**
If mapping is not empty then this function computes the minimal (maximum) key of the <code>mapping</code>, deletes that key and the associated value from the <code>mapping</code> and returns an <code>optional</code> value containing that key and the associated value. Returns an empty <code>optional</code> if there is no such key.							*/
							function delMin() returns (optional(KeyType, ValueType));
							/**
							If mapping is not empty then this function computes the minimal (maximum) key of the <code>mapping</code>, deletes that key and the associated value from the <code>mapping</code> and returns an <code>optional</code> value containing that key and the associated value. Returns an empty <code>optional</code> if there is no such key.							*/
							function delMax() returns (optional(KeyType, ValueType));
							/**
Checks whether <strong>key</strong> is present in the <code>mapping</code> and returns an <code>optional</code> with the associated value. Returns an empty <code>optional</code> if there is no such key.
							*/
							function fetch(KeyType key) returns (optional(ValueType));
							/**
Returns whether key is present in the <code>mapping</code>.
							*/
							function exists(KeyType key) returns (bool);
							/**
Returns whether the <code>mapping</code> is empty.
							*/
							function empty() returns (bool);
							/**
Sets the value associated with key only if key is present in the <code>mapping</code> and returns the success flag.
							*/
							function replace(KeyType key, ValueType value) returns (bool);
							/**
Sets the value associated with key only if key is not present in the <code>mapping</code>.
							*/
							function add(KeyType key, ValueType value) returns (bool);
							/**
Sets the value associated with key, but also returns an <code>optional</code> with the previous value associated with the <strong>key</strong>, if any. Otherwise, returns an empty <code>optional</code>.
							*/
							function getSet(KeyType key, ValueType value) returns (optional(ValueType));
							/**
Sets the value associated with <stong>key</stong>, but only if key is not present in the <strong>mapping</strong>. Returns an <code>optional</code> with the old value without changing the dictionary if that value is present in the <code>mapping</code>, otherwise returns an empty <code>optional</code>.
							*/
							function getAdd(KeyType key, ValueType value) returns (optional(ValueType));
							/**
Sets the value associated with key, but only if key is present in the <code>mapping</code>. On success, returns an <code>optional</code> with the old value associated with the key. Otherwise, returns an empty <code>optional</code>.
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
    """)
  }

  val arrayType: SolContract by lazy {
    contract("""
      /**
       @custom:typeArgument Type=type
      */
      contract ${internalise("Array")} {
            /**
            * yields the fixed length of the byte array. The length of memory arrays is fixed (but dynamic, i.e. it can depend on runtime parameters) once they are created.
            */
          uint256 length;
          
            /**
Returns status flag whether the array is empty (its length is 0).
            */
          function empty() returns (bool);
          
            /**
Dynamic storage arrays and <code>bytes</code> (not <code>string</code>) have a member function called <code>push()</code> that you can use to append a zero-initialised element at the end of the array. It returns a reference to the element, so that it can be used like <code>x.push().t = 2</code> or <code>x.push() = b</code>.
            */
          function push() returns (Type);
            /**
Dynamic storage arrays and <code>bytes</code> (not <code>string</code>) have a member function called <code>push(x)</code> that you can use to append a given element at the end of the array. The function returns nothing.
            */
          function push(Type value);
            /**
Dynamic storage arrays and <code>bytes</code> (not <code>string</code>) have a member function called <code>pop()</code> that you can use to remove an element from the end of the array. This also implicitly calls delete on the removed element. The function returns nothing.            */
          function pop() returns (Type);
      }
    """)
  }

  val optionalType: SolContract by lazy {
    contract("""
      /**
       @custom:typeArgument Type=T0
      */
      contract ${internalise("Optional")} {

							/**
Checks whether the <code>optional</code> contains a value.
							*/
							function hasValue() returns (bool);
							/**
Returns the contained value, if the <code>optional</code> contains one. Otherwise, throws an exception.
							*/
							function get() returns (Type);
							/**
Replaces content of the <code>optional</code> with <strong>value</strong>.
							*/
							function set(Type value);
							/**
Deletes content of the <code>optional</code>.
							*/
							function reset();
      }
    """)
  }

  val vectorType: SolContract by lazy {
    contract("""
      /**
       @custom:typeArgument Type=T0
      */
      contract ${internalise("Vector")} {

							/**
Appends <b>obj</b> to the <code>vector</code>.
							*/
							function push(Type _type);
							/**
Pops the last value from the <code>vector</code> and returns it.
							*/
							function pop() returns (Type);
							/**
Returns length of the <code>vector</code>.
							*/
							function length() returns (uint8);
							/**
Checks whether the <code>vector</code> is empty.
							*/
							function empty() returns (bool);
      }
    """)
  }


  val abiType: SolContract by lazy {
    contract("""
      contract ${internalise("Abi")} {
							/**
                creates <code>cell</code> from the values.
                @custom:no_validation
                @custom:typeArgument Type
							*/
							function encode(Type varargs) returns (TvmCell /*cell*/);
							/**
                decodes the <code>cell</code> and returns the values.
               @custom:no_validation
               @custom:typeArgument Type:TypeSequence
							*/
							function decode(TvmCell cell, Type varargs) returns (Type);
      }
    """)
  }

  val structType: SolContract by lazy {
    contract("""
      contract ${internalise("Struct")} {
							/**
               @custom:typeArgument Type:DecodedElement
							*/
							function unpack() returns (Type);
      }
    """)
  }

  val stringType: SolContract by lazy {
    contract("""
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
							function toUpperCase() returns (string);
							/**
							*/
							function toLowerCase() returns (string);
        }
    """)
  }

  val rndType: SolContract by lazy {
    contract("""
      contract ${internalise("Rnd")} {
              /**
               @custom:typeArgument Type:Int
              */
              function next(Type limit) returns (Type); 
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
    """)
  }


  val bytesType: SolContract by lazy {
    contract("""
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
    """)
  }

  val tvmType: SolContract by lazy {
    contract("""
      contract ${internalise("Tvm")} {
							/**
Executes TVM instruction "ACCEPT" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.2). This instruction sets current gas limit to its maximal allowed value. This action is required to process external messages that bring no value.
							*/
							function accept();
							/**
Executes TVM instruction "SETGASLIMIT" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.2). Sets current gas limit <strong>gl</strong> to the minimum of <strong>g</strong> and <code>gm</code>, and resets the gas credit <strong>gc</strong> to zero. If the gas consumed so far (including the present instruction) exceeds the resulting value of <code>gl</code>, an (unhandled) out of gas exception is thrown before setting new gas limits. Notice that <code>tvm.setGasLimit(...)</code> with an argument <stong>g ≥ 2^63</stong> - 1 is equivalent to <code>tvm.accept()</code>. <code>tvm.setGasLimit()</code> is similar to <code>tvm.accept()</code>. <code>tvm.accept()</code> sets gas limit gl to the maximum possible value (depends on the network configuration parameters, usually is equal to 1_000_000 units of gas). <code>tvm.setGasLimit()</code> is generally used for accepting external messages and restricting max possible gas consumption. It may be used to protect from flood by "bad" owner in a contract that is used by multiple users. Let's consider some scenario:

<pre>1. Check whether msg.pubkey() != 0 and msg.pubkey() belongs to the list of trusted public keys;
2. Check whether m_floodCounter[msg.pubkey()] < 5 where m_floodCounter is count of pending operations of msg.pubkey() user.
3. <code>tvm.setGasLimit(75_000);</code> accept external message and set gas limit to 75_000.
4. <code>++m_floodCounter[msg.pubkey()];</code> increase count of pending operations for current users.
5. <code>tvm.commit();</code> save current state if it needs.
6. Do other things.</pre>
So if some user's public key will be stolen, then a hacker can spam with external messages and burn at most <code>5 * 75_000</code> units of gas instead of <code>5 * 1_000_000</code>, because we use <code>tvm.setGasLimit()</code> instead of <code>tvm.accept()</code>.							*/
							function setGasLimit(uint g);
							/**
Computes the amount of gas that can be bought for <code>value</code> nanotons, and sets <strong>gl</strong>
accordingly in the same way as <code><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmsetgaslimit">tvm.setGasLimit()</a></code>.
							*/
							function buyGas(uint value);
							/**
Creates a "check point" of the state variables (by copying them from c7 to c4) and register c5. If the contract throws an exception at the computing phase then the state variables and register c5 will roll back to the "check point", and the computing phase will be considered "successful". If contract doesn't throw an exception, it has no effect.
							*/
							function commit();
							/**
Same as <code><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmcommit">tvm.commit()</a></code> but doesn't copy the state variables from c7 to c4. It's a wrapper for opcode <code>COMMIT</code>. See <a href="https://test.ton.org/tvm.pdf">TVM</a>.

<strong>Note</strong>: Don't use <code>tvm.rawCommit()</code> after <code>tvm.accept()</code> in processing external messages because you don't save from c7 to c4 the hidden state variable <code>timestamp</code> that is used for replay protection.
							*/
							function rawCommit();
							/**
<strong>Note</strong>: Function is experimental.

A dual of the <code>tvm.setData()</code>function. It returns value of the <code>c4</code> register. Obtaining a raw storage cell can be useful when upgrading a new version of the contract that introduces an altered data layout.

Manipulation with a raw storage cell requires understanding of the way the compiler stores the data. Refer to the description of <code>tvm.setData()</code> below to get more details.

<stong>Note</stong>: state variables and replay protection timestamp stored in the data cell have the same values that were before the transaction. See <code><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmcommit">tvm.commit()</a></code> to learn about register <code>c4</code> update.
							*/
							function getData() returns (TvmCell);
							/**
<strong>Note</strong>: Function is experimental.

Stores cell <code>data</code> in the register <code>c4</code>. Mind that after returning from a public function all state variables from <code>c7</code> are copied to <code>c4</code> and <code>tvm.setData</code> will have no effect. Example hint, how to set <code>c4</code>:

<code><pre>TvmCell data = ...;
	tvm.setData(data); // set register c4
	tvm.rawCommit();   // save register c4 and c5
	revert(200);       // throw the exception to terminate the transaction</pre></code>
	Be careful with the hidden state variable <code>timestamp</code> and think about possibility of external messages replaying.
							*/
							function setData(TvmCell data);
							/**
Dumps <code>log</code> string. This function is a wrapper for <a href="https://test.ton.org/tvm.pdf">TVM</a> instructions <code>PRINTSTR</code> (for constant literal strings shorter than 16 symbols) and <code>STRDUMP</code> (for other strings). <code>logtvm</code> is an alias for <code>tvm.log(string)</code>. Example:

<code><pre>tvm.log("Hello, world!");
	logtvm("99_Bottles");
	
	string s = "Some_text";
	tvm.log(s);</pre></code>
<strong>Note</strong>: For long strings dumps only the first 127 symbols.
							*/
							function log(string log);
							/**
Dumps cell data or integer. Note that for cells this function dumps data only from the first cell. <code>T</code> must be an integer type or <code>TvmCell</code>.

Example:

<code><pre>TvmBuilder b;
	b.storeUnsigned(0x9876543210, 40);
	TvmCell c = b.toCell();
	tvm.hexdump(c);
	tvm.bindump(c);
	uint a = 123;
	tvm.hexdump(a);
	tvm.bindump(a);
	int b = -333;
	tvm.hexdump(b);
	tvm.bindump(b);</pre></code>
Expected output for the example:

<code><pre>CS<9876543210>(0..40)
	CS<10011000011101100101010000110010000100001>(0..40)
	7B
	1111011
	-14D
	-101001101</pre></code>
							*/
							function hexdump(TvmCell a);
              function hexdump(int a);
              function hexdump(uint a);
							/**
Dumps cell data or integer. Note that for cells this function dumps data only from the first cell. <code>T</code> must be an integer type or TvmCell.

Example:

<code><pre>TvmBuilder b;
	b.storeUnsigned(0x9876543210, 40);
	TvmCell c = b.toCell();
	tvm.hexdump(c);
	tvm.bindump(c);
	uint a = 123;
	tvm.hexdump(a);
	tvm.bindump(a);
	int b = -333;
	tvm.hexdump(b);
	tvm.bindump(b);</pre></code>
Expected output for the example:

<code><pre>CS<9876543210>(0..40)
	CS<10011000011101100101010000110010000100001>(0..40)
	7B
	1111011
	-14D
	-101001101</pre></code>
							*/
							function bindump(TvmCell a);
              function bindump(int a);
              function bindump(uint a);

							/**
This command creates an output action that would change this smart contract code to that given by the <code>TvmCell</code> <storng>newCode</storng> (this change will take effect only after the successful termination of the current run of the smart contract).

See example of how to use this function:

<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/12_BadContract.sol"> old contract</a></li>
<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/12_NewVersion.sol">new contract</a></li>
							*/
							function setcode(TvmCell newCode);
							/**
							Executes TVM instruction "CONFIGPARAM" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.4. - F832). This command returns the value of the global configuration parameter with integer index <strong>paramNumber</strong>. Argument should be an integer literal. Supported <strong>paramNumber</strong>: 1, 15, 17, 34.
							*/
							function configParam(uint8 paramNumber) returns (TypeA a, TypeB b);
							/**
							Executes TVM instruction "CONFIGPARAM" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.4. - F832). Returns the value of the global configuration parameter with integer index paramNumber as a <code>TvmCell</code> and a boolean status.
							*/
							function rawConfigParam(uint8 paramNumber) returns (TvmCell cell, bool status); 
							/**
							Creates an output action that reserves reserve nanotons. It is roughly equivalent to create an outbound message carrying reserve nanotons to oneself, so that the subsequent output actions would not be able to spend more money than the remainder. It's a wrapper for opcodes "RAWRESERVE" and "RAWRESERVEX". See <a href="https://test.ton.org/tvm.pdf">TVM</a>.

							Let's denote:
							<li><code>original_balance</code>is balance of the contract before the computing phase that is equal to balance of the contract before the transaction minus storage fee. Note: <code>original_balance</code> doesn't include <code>msg.value</code> and <code>original_balance</code> is not equal to <code>address(this).balance</code>.</li>
							<li><code>remaining_balance</code>is contract's current remaining balance at the action phase after some handled actions and before handing the "rawReserve" action.</li>
							
							Let's consider how much nanotons (reserve) are reserved in all cases of flag:
							<li>0 -> <code>reserve = value</code> nanotons.</li>
							<li>1 -> <code>reserve = remaining_balance - value</code> nanotons.</li>
							<li>2 -> <code>reserve = min(value, remaining_balance)</code> nanotons.</li>
							<li>3 = 2 + 1 -> <code>reserve = remaining_balance - min(value, remaining_balance)</code> nanotons.</li>
							<li>4 -> <code>reserve = original_balance + value</code> nanotons.</li>
							<li>5 = 4 + 1 -> <code>reserve = remaining_balance - (original_balance + value)</code> nanotons.</li>
							<li>6 = 4 + 2 -> <code>reserve = min(original_balance + value, remaining_balance) = remaining_balance</code> nanotons.</li>
							<li>7 = 4 + 2 + 1 -> <code>reserve = remaining_balance - min(original_balance + value, remaining_balance)</code> nanotons.</li>
							<li>12 = 8 + 4 -> <code>reserve = original_balance - value</code> nanotons.</li>
							<li>13 = 8 + 4 + 1 -> <code>reserve = remaining_balance - (original_balance - value)</code> nanotons.</li>
							<li>14 = 8 + 4 + 2 -> <code>reserve = min(original_balance - value, remaining_balance)</code> nanotons.</li>
							<li>15 = 8 + 4 + 2 + 1 -> <code>reserve = remaining_balance - min(original_balance - value, remaining_balance)</code> nanotons.</li>

							All other values of <code>flag</code> are invalid.
							
							To make it clear, let's consider the order of <code>reserve</code> calculation:

							<pre>1. if <code>flag</code> has bit <code>+8</code> then <code>value = -value</code>.
							2. if <code>flag</code> has bit <code>+4</code> then <code>value += original_balance</code>.
							3. Check <code>value >= 0</code>.
							4. if <code>flag</code> has bit <code>+2</code> then <code>value = min(value, remaining_balance)</code>.
							5. if <code>flag</code> has bit +1 then <code>value = remaining_balance - value</code>.
							6. <code>reserve = value</code>.
							7. Check <code>0 <= reserve <= remaining_balance</code>.</pre>

							Example: 
							<code>tvm.rawReserve(1 ton, 4 + 8);</code>
							*/
							function rawReserve(uint value, uint8 flag);
							/**
							Creates an output action that reserves reserve nanotons. It is roughly equivalent to create an outbound message carrying reserve nanotons to oneself, so that the subsequent output actions would not be able to spend more money than the remainder. It's a wrapper for opcodes "RAWRESERVE" and "RAWRESERVEX". See <a href="https://test.ton.org/tvm.pdf">TVM</a>.

							Let's denote:
							<li><code>original_balance</code>is balance of the contract before the computing phase that is equal to balance of the contract before the transaction minus storage fee. Note: <code>original_balance</code> doesn't include <code>msg.value</code> and <code>original_balance</code> is not equal to <code>address(this).balance</code>.</li>
							<li><code>remaining_balance</code>is contract's current remaining balance at the action phase after some handled actions and before handing the "rawReserve" action.</li>
							
							Let's consider how much nanotons (reserve) are reserved in all cases of flag:
							<li>0 -> <code>reserve = value</code> nanotons.</li>
							<li>1 -> <code>reserve = remaining_balance - value</code> nanotons.</li>
							<li>2 -> <code>reserve = min(value, remaining_balance)</code> nanotons.</li>
							<li>3 = 2 + 1 -> <code>reserve = remaining_balance - min(value, remaining_balance)</code> nanotons.</li>
							<li>4 -> <code>reserve = original_balance + value</code> nanotons.</li>
							<li>5 = 4 + 1 -> <code>reserve = remaining_balance - (original_balance + value)</code> nanotons.</li>
							<li>6 = 4 + 2 -> <code>reserve = min(original_balance + value, remaining_balance) = remaining_balance</code> nanotons.</li>
							<li>7 = 4 + 2 + 1 -> <code>reserve = remaining_balance - min(original_balance + value, remaining_balance)</code> nanotons.</li>
							<li>12 = 8 + 4 -> <code>reserve = original_balance - value</code> nanotons.</li>
							<li>13 = 8 + 4 + 1 -> <code>reserve = remaining_balance - (original_balance - value)</code> nanotons.</li>
							<li>14 = 8 + 4 + 2 -> <code>reserve = min(original_balance - value, remaining_balance)</code> nanotons.</li>
							<li>15 = 8 + 4 + 2 + 1 -> <code>reserve = remaining_balance - min(original_balance - value, remaining_balance)</code> nanotons.</li>

							All other values of <code>flag</code> are invalid.
							
							To make it clear, let's consider the order of <code>reserve</code> calculation:

							<pre>1. if <code>flag</code> has bit <code>+8</code> then <code>value = -value</code>.
							2. if <code>flag</code> has bit <code>+4</code> then <code>value += original_balance</code>.
							3. Check <code>value >= 0</code>.
							4. if <code>flag</code> has bit <code>+2</code> then <code>value = min(value, remaining_balance)</code>.
							5. if <code>flag</code> has bit +1 then <code>value = remaining_balance - value</code>.
							6. <code>reserve = value</code>.
							7. Check <code>0 <= reserve <= remaining_balance</code>.</pre>
							*/
							function rawReserve(uint value, ExtraCurrencyCollection currency, uint8 flag);
							/**
							Returns the initial code hash that contract had when it was deployed.
							*/
							function initCodeHash() returns (uint256 hash); 
							/**
							Executes TVM instruction "HASHCU" or "HASHSU" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F900). It computes the representation hash of a given argument and returns it as a 256-bit unsigned integer. For <code>string</code> and <code>bytes</code> it computes hash of the tree of cells that contains data but not data itself. See <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#sha256">sha256</a> to count hash of data.
							
							Example: 

							<code><pre>uint256 hash = tvm.hash(TvmCell cellTree);
							uint256 hash = tvm.hash(string);
							uint256 hash = tvm.hash(bytes);</pre></code>
							*/
							function hash(TvmCell cellTree) returns (uint256); 
							/**
							Executes TVM instruction "HASHCU" or "HASHSU" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F900). It computes the representation hash of a given argument and returns it as a 256-bit unsigned integer. For <code>string</code> and <code>bytes</code> it computes hash of the tree of cells that contains data but not data itself. See <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#sha256">sha256</a> to count hash of data.
							
							Example: 

							<code><pre>uint256 hash = tvm.hash(TvmCell cellTree);
							uint256 hash = tvm.hash(string);
							uint256 hash = tvm.hash(bytes);</pre></code>
							*/
							function hash(string data) returns (uint256); 
							/**
							Executes TVM instruction "HASHCU" or "HASHSU" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F900). It computes the representation hash of a given argument and returns it as a 256-bit unsigned integer. For <code>string</code> and <code>bytes</code> it computes hash of the tree of cells that contains data but not data itself. See <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#sha256">sha256</a> to count hash of data.
							
							Example: 

							<code><pre>uint256 hash = tvm.hash(TvmCell cellTree);
							uint256 hash = tvm.hash(string);
							uint256 hash = tvm.hash(bytes);</pre></code>
							*/
							function hash(bytes data) returns (uint256); 
							/**
							Executes TVM instruction "HASHCU" or "HASHSU" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F900). It computes the representation hash of a given argument and returns it as a 256-bit unsigned integer. For <code>string</code> and <code>bytes</code> it computes hash of the tree of cells that contains data but not data itself. See <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#sha256">sha256</a> to count hash of data.
							
							Example: 

							<code><pre>uint256 hash = tvm.hash(TvmCell cellTree);
							uint256 hash = tvm.hash(string);
							uint256 hash = tvm.hash(bytes);</pre></code>
							*/
							function hash(TvmSlice data) returns (uint256); 
							/**
							Executes TVM instruction "CHKSIGNU" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F910) for variants 1 and 2. This command checks the Ed25519-signature of the <strong>hash</strong> using public key <strong>pubkey</strong>>. Signature is represented by two uint256 <strong>SignHighPart</strong> and <strong>SignLowPart</strong> in the first variant and by the slice <strong>signature</strong> in the second variant. In the third variant executes TVM instruction "CHKSIGNS" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F911). This command checks Ed25519-signature of the data using public key pubkey. Signature is represented by the slice <strong>signature</strong>.

							Example:

							<code><pre>uint256 hash;
							uint256 SignHighPart;
							uint256 SignLowPart;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, SignHighPart, SignLowPart, pubkey);  // 1 variant
								
							uint256 hash;
							TvmSlice signature;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, signature, pubkey);  // 2 variant
								
							TvmSlice data;
							TvmSlice signature;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, signature, pubkey);  // 3 variant</pre></code>
							*/
							function checkSign(uint256 hash, uint256 SignHighPart, uint256 SignLowPart, uint256 pubkey) returns (bool); 
							/**
							Executes TVM instruction "CHKSIGNU" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F910) for variants 1 and 2. This command checks the Ed25519-signature of the <strong>hash</strong> using public key <strong>pubkey</strong>>. Signature is represented by two uint256 <strong>SignHighPart</strong> and <strong>SignLowPart</strong> in the first variant and by the slice <strong>signature</strong> in the second variant. In the third variant executes TVM instruction "CHKSIGNS" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F911). This command checks Ed25519-signature of the data using public key pubkey. Signature is represented by the slice <strong>signature</strong>.

							Example:

							<code><pre>uint256 hash;
							uint256 SignHighPart;
							uint256 SignLowPart;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, SignHighPart, SignLowPart, pubkey);  // 1 variant
								
							uint256 hash;
							TvmSlice signature;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, signature, pubkey);  // 2 variant
								
							TvmSlice data;
							TvmSlice signature;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, signature, pubkey);  // 3 variant</pre></code>
							*/
							function checkSign(uint256 hash, TvmSlice signature, uint256 pubkey) returns (bool); 
							/**
							Executes TVM instruction "CHKSIGNU" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F910) for variants 1 and 2. This command checks the Ed25519-signature of the <strong>hash</strong> using public key <strong>pubkey</strong>>. Signature is represented by two uint256 <strong>SignHighPart</strong> and <strong>SignLowPart</strong> in the first variant and by the slice <strong>signature</strong> in the second variant. In the third variant executes TVM instruction "CHKSIGNS" (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.6. - F911). This command checks Ed25519-signature of the data using public key pubkey. Signature is represented by the slice <strong>signature</strong>.

							Example:

							<code><pre>uint256 hash;
							uint256 SignHighPart;
							uint256 SignLowPart;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, SignHighPart, SignLowPart, pubkey);  // 1 variant
								
							uint256 hash;
							TvmSlice signature;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, signature, pubkey);  // 2 variant
								
							TvmSlice data;
							TvmSlice signature;
							uint256 pubkey;
							bool signatureIsValid = tvm.checkSign(hash, signature, pubkey);  // 3 variant</pre></code>
							*/
							function checkSign(TvmSlice data, TvmSlice signature, uint256 pubkey) returns (bool); 
							/**
							Inserts a public key into the <code>stateInit</code> data field. If the <code>stateInit</code> has wrong format then throws an exception.
							*/
							function insertPubkey(TvmCell stateInit, uint256 pubkey) returns (TvmCell); 
							/**
							Generates a <code>StateInit</code> (<a href="https://test.ton.org/tblkch.pdf">TBLKCH</a> - 3.1.7.) from <code>code</code> and <code>data</code> <code>TvmCell</code>s. Member <code>splitDepth</code> of the tree of cell <code>StateInit</code>:

							1. is not set. Has no value.
							2. is set. <code>0 <= splitDepth <= 31</code>
							3. Arguments can also be set with names. List of possible names:

							<li><code>code</code> (<code>TvmCell</code>) - defines the code field of the <code>StateInit</code>. Must be specified.</li>
							<li><code>data</code> (<code>TvmCell</code>) - defines the data field of the <code>StateInit</code>. Conflicts with <code>pubkey</code> and <code>varInit</code>. Can be omitted, in this case data field would be build from <code>pubkey</code> and <code>varInit</code>.</li>
							<li><code>splitDepth</code>(<code>uint8</code>) - splitting depth. <code>0 <= splitDepth <= 31</code>. Can be omitted. By default, it has no value.</li>
							<li><code>pubkey</code> (<code>uint256</code>) - defines the public key of the new contract. Conflicts with <code>data</code>. Can be omitted, default value is 0.</li>
							<li><code>varInit</code> (<code>initializer list</code>) - used to set <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#keyword-static">static</a> variables of the contract. Conflicts with <code>data</code> and requires <code>contr</code> to be set. Can be omitted.</li>
							<li><code>contr</code> (<code>contract</code>) - defines the contract whose <code>StateInit</code> is being built. Mandatory to be set if the option <code>varInit</code> is specified.</li>

							Examples of this function usage:
							<code><pre>contract A {
								uint static var0;
								address static var1;
							}
							
							contract C {
							
								function f() public pure {
									TvmCell code;
									TvmCell data;
									uint8 depth;
									TvmCell stateInit = tvm.buildStateInit(code, data);
									stateInit = tvm.buildStateInit(code, data, depth);
								}
							
								function f1() public pure {
									TvmCell code;
									TvmCell data;
									uint8 depth;
									uint pubkey;
									uint var0;
									address var1;
							
									TvmCell stateInit1 = tvm.buildStateInit({code: code, data: data, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code, splitDepth: depth, varInit: {var0: var0, var1: var1}, pubkey: pubkey, contr: A});
									stateInit1 = tvm.buildStateInit({varInit: {var0: var0, var1: var1}, pubkey: pubkey, contr: A, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, pubkey: pubkey, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, pubkey: pubkey, code: code});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({pubkey: pubkey, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code});
								}
							}</pre></code>
							*/
							function buildStateInit(TvmCell code, TvmCell data) returns (TvmCell stateInit); 
							/**
							Generates a <code>StateInit</code> (<a href="https://test.ton.org/tblkch.pdf">TBLKCH</a> - 3.1.7.) from <code>code</code> and <code>data</code> <code>TvmCell</code>s. Member <code>splitDepth</code> of the tree of cell <code>StateInit</code>:

							1. is not set. Has no value.
							2. is set. <code>0 <= splitDepth <= 31</code>
							3. Arguments can also be set with names. List of possible names:

							<li><code>code</code> (<code>TvmCell</code>) - defines the code field of the <code>StateInit</code>. Must be specified.</li>
							<li><code>data</code> (<code>TvmCell</code>) - defines the data field of the <code>StateInit</code>. Conflicts with <code>pubkey</code> and <code>varInit</code>. Can be omitted, in this case data field would be build from <code>pubkey</code> and <code>varInit</code>.</li>
							<li><code>splitDepth</code>(<code>uint8</code>) - splitting depth. <code>0 <= splitDepth <= 31</code>. Can be omitted. By default, it has no value.</li>
							<li><code>pubkey</code> (<code>uint256</code>) - defines the public key of the new contract. Conflicts with <code>data</code>. Can be omitted, default value is 0.</li>
							<li><code>varInit</code> (<code>initializer list</code>) - used to set <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#keyword-static">static</a> variables of the contract. Conflicts with <code>data</code> and requires <code>contr</code> to be set. Can be omitted.</li>
							<li><code>contr</code> (<code>contract</code>) - defines the contract whose <code>StateInit</code> is being built. Mandatory to be set if the option <code>varInit</code> is specified.</li>

							Examples of this function usage:
							<code><pre>contract A {
								uint static var0;
								address static var1;
							}
							
							contract C {
							
								function f() public pure {
									TvmCell code;
									TvmCell data;
									uint8 depth;
									TvmCell stateInit = tvm.buildStateInit(code, data);
									stateInit = tvm.buildStateInit(code, data, depth);
								}
							
								function f1() public pure {
									TvmCell code;
									TvmCell data;
									uint8 depth;
									uint pubkey;
									uint var0;
									address var1;
							
									TvmCell stateInit1 = tvm.buildStateInit({code: code, data: data, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code, splitDepth: depth, varInit: {var0: var0, var1: var1}, pubkey: pubkey, contr: A});
									stateInit1 = tvm.buildStateInit({varInit: {var0: var0, var1: var1}, pubkey: pubkey, contr: A, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, pubkey: pubkey, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, pubkey: pubkey, code: code});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({pubkey: pubkey, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code});
								}
							}</pre></code>
							*/
							function buildStateInit(TvmCell code, TvmCell data, uint8 splitDepth) returns (TvmCell stateInit);
							/**
							Generates a <code>StateInit</code> (<a href="https://test.ton.org/tblkch.pdf">TBLKCH</a> - 3.1.7.) from <code>code</code> and <code>data</code> <code>TvmCell</code>s. Member <code>splitDepth</code> of the tree of cell <code>StateInit</code>:

							1. is not set. Has no value.
							2. is set. <code>0 <= splitDepth <= 31</code>
							3. Arguments can also be set with names. List of possible names:

							<li><code>code</code> (<code>TvmCell</code>) - defines the code field of the <code>StateInit</code>. Must be specified.</li>
							<li><code>data</code> (<code>TvmCell</code>) - defines the data field of the <code>StateInit</code>. Conflicts with <code>pubkey</code> and <code>varInit</code>. Can be omitted, in this case data field would be build from <code>pubkey</code> and <code>varInit</code>.</li>
							<li><code>splitDepth</code>(<code>uint8</code>) - splitting depth. <code>0 <= splitDepth <= 31</code>. Can be omitted. By default, it has no value.</li>
							<li><code>pubkey</code> (<code>uint256</code>) - defines the public key of the new contract. Conflicts with <code>data</code>. Can be omitted, default value is 0.</li>
							<li><code>varInit</code> (<code>initializer list</code>) - used to set <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#keyword-static">static</a> variables of the contract. Conflicts with <code>data</code> and requires <code>contr</code> to be set. Can be omitted.</li>
							<li><code>contr</code> (<code>contract</code>) - defines the contract whose <code>StateInit</code> is being built. Mandatory to be set if the option <code>varInit</code> is specified.</li>

							Examples of this function usage:
							<code><pre>contract A {
								uint static var0;
								address static var1;
							}
							
							contract C {
							
								function f() public pure {
									TvmCell code;
									TvmCell data;
									uint8 depth;
									TvmCell stateInit = tvm.buildStateInit(code, data);
									stateInit = tvm.buildStateInit(code, data, depth);
								}
							
								function f1() public pure {
									TvmCell code;
									TvmCell data;
									uint8 depth;
									uint pubkey;
									uint var0;
									address var1;
							
									TvmCell stateInit1 = tvm.buildStateInit({code: code, data: data, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code, splitDepth: depth, varInit: {var0: var0, var1: var1}, pubkey: pubkey, contr: A});
									stateInit1 = tvm.buildStateInit({varInit: {var0: var0, var1: var1}, pubkey: pubkey, contr: A, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, pubkey: pubkey, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, pubkey: pubkey, code: code});
									stateInit1 = tvm.buildStateInit({contr: A, varInit: {var0: var0, var1: var1}, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({pubkey: pubkey, code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code, splitDepth: depth});
									stateInit1 = tvm.buildStateInit({code: code});
								}
							}</pre></code>
							*/
							function buildStateInit(TvmCell code, TvmCell data, uint8 splitDepth, uint256 pubkey, Contract contr, VarInit varInit);
							/**
							Calculates hash of the stateInit for given code and data specifications.

							Example:
							<code><pre>TvmCell code = ...;
							TvmCell data = ...;
							uint codeHash = tvm.hash(code);
							uint dataHash = tvm.hash(data);
							uint16 codeDepth = code.depth();
							uint16 dataDepth = data.depth();
							uint256 hash = tvm.stateInitHash(codeHash, dataHash, codeDepth, dataDepth);</pre></code>

							See also <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/docs/internal/stateInit_hash.md">internal doc</a> to read more about this function mechanics.
							*/
							function stateInitHash(uint256 codeHash, uint256 dataHash, uint16 codeDepth, uint16 dataDepth) returns (uint256);
							/**
							Returns contract's code.
							
							See <a href="https://github.com/tonlabs/samples/blob/master/solidity/21_self_deploy.sol">SelfDeployer</a>
							*/
							function code() returns (TvmCell);
							/**
							If <strong>code</strong> contains salt then <strong>optSalt</strong> contains one. Otherwise, <strong>optSalt</strong> doesn't contain any value.
							*/
							function codeSalt(TvmCell code) returns (optional(TvmCell) optSalt);
							/**
							Inserts <strong>salt</strong> into <strong>code</strong> and returns new code <strong>newCode</strong>.
							*/
							function setCodeSalt(TvmCell code, TvmCell salt) returns (TvmCell newCode); 
							/**
							Returns contract's public key, stored in contract data. If key is not set, function returns 0.
							*/
							function pubkey() returns (uint256);
							/**
							Set new contract's public key. Contract's public key can be obtained from <code>tvm.pubkey</code>.
							*/
							function setPubkey(uint256 newPubkey); 
							/**
							Changes this smart contract current code to that given by Cell <strong>newCode</strong>. Unlike <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmsetcode">tvm.setcode()</a> this function changes code of the smart contract only for current TVM execution, but has no effect after termination of the current run of the smart contract.

							See example of how to use this function:
							<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/12_BadContract.sol">old contract</a></li>
							<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/12_NewVersion.sol">new contract</a></li>
							*/
							function setCurrentCode(TvmCell newCode); 
							/**
							Resets all state variables to their default values.
							*/
							function resetStorage();
							/**
							*/
							function functionId(Function functionName) returns (uint32);
							/**
							Returns the function id (uint32) of a public/external function or constructor.

							Example:
							<code><pre>contract MyContract {
								constructor(uint a) public {
								}
									//...
								}
							
								function f() public pure returns (uint) {
									//...
								}
							
								function getConstructorID() public pure returns (uint32) {
									uint32 functionId = tvm.functionId(MyContract);
									return functionId;
								}
							
								function getFuncID() public pure returns (uint32) {
									uint32 functionId = tvm.functionId(f);
									return functionId;
								}
							}</pre></code>
							See example of how to use this function:
							<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/16_onBounceHandler.sol"></a>onBounceHandler</li>
							*/
							function functionId(Contract ContractName) returns (uint32); 
							/**
							Constructs a message body for the function call. Body can be used as a payload for <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#addresstransfer"><address>.transfer()</a> If the function is <code>responsible</code>, <strong>callbackFunction</strong> must be set.

							Example:
							<code><pre>contract Remote {
								constructor(uint x, uint y, uint z) public { /.../ }
								function func(uint256 num, int64 num2) public pure { /.../ }
								function getCost(uint256 num) public responsible pure returns (uint128) { /.../ }
							}
							
							// deploy the contract
							TvmCell body = tvm.encodeBody(Remote, 100, 200, 300);
							addr.transfer({value: 10 ton, body: body, stateInit: stateInit });
							
							// call the function
							TvmCell body = tvm.encodeBody(Remote.func, 123, -654);
							addr.transfer({value: 10 ton, body: body});
							
							// call the responsible function
							TvmCell body = tvm.encodeBody(Remote.getCost, onGetCost, 105);
							addr.transfer({value: 10 ton, body: body});</pre></code>

							See also: 
							<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#external-function-calls">External function calls</a></li>
							<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmslicedecodefunctionparams"><TvmSlice>.decodeFunctionParams()</a></li>
							<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmbuildintmsg">tvm.buildIntMsg()</a></li>
							*/
							function encodeBody(functionName, arg0, arg1, arg2) returns (TvmCell);
							/**
							Constructs a message body for the function call. Body can be used as a payload for <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#addresstransfer"><address>.transfer()</a> If the function is <code>responsible</code>, <strong>callbackFunction</strong> must be set.

								Example:
								<code><pre>contract Remote {
									constructor(uint x, uint y, uint z) public { /.../ }
									function func(uint256 num, int64 num2) public pure { /.../ }
									function getCost(uint256 num) public responsible pure returns (uint128) { /.../ }
								}
								
								// deploy the contract
								TvmCell body = tvm.encodeBody(Remote, 100, 200, 300);
								addr.transfer({value: 10 ton, body: body, stateInit: stateInit });
								
								// call the function
								TvmCell body = tvm.encodeBody(Remote.func, 123, -654);
								addr.transfer({value: 10 ton, body: body});
								
								// call the responsible function
								TvmCell body = tvm.encodeBody(Remote.getCost, onGetCost, 105);
								addr.transfer({value: 10 ton, body: body});</pre></code>
	
								See also: 
								<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#external-function-calls">External function calls</a></li>
								<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmslicedecodefunctionparams"><TvmSlice>.decodeFunctionParams()</a></li>
								<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmbuildintmsg">tvm.buildIntMsg()</a></li>
							*/
							function encodeBody(functionName, callbackFunction, arg0, arg1, arg2) returns (TvmCell);
							/**
							Constructs a message body for the function call. Body can be used as a payload for <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#addresstransfer"><address>.transfer()</a> If the function is <code>responsible</code>, <strong>callbackFunction</strong> must be set.

								Example:
								<code><pre>contract Remote {
									constructor(uint x, uint y, uint z) public { /.../ }
									function func(uint256 num, int64 num2) public pure { /.../ }
									function getCost(uint256 num) public responsible pure returns (uint128) { /.../ }
								}
								
								// deploy the contract
								TvmCell body = tvm.encodeBody(Remote, 100, 200, 300);
								addr.transfer({value: 10 ton, body: body, stateInit: stateInit });
								
								// call the function
								TvmCell body = tvm.encodeBody(Remote.func, 123, -654);
								addr.transfer({value: 10 ton, body: body});
								
								// call the responsible function
								TvmCell body = tvm.encodeBody(Remote.getCost, onGetCost, 105);
								addr.transfer({value: 10 ton, body: body});</pre></code>
	
								See also: 
								<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#external-function-calls">External function calls</a></li>
								<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmslicedecodefunctionparams"><TvmSlice>.decodeFunctionParams()</a></li>
								<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmbuildintmsg">tvm.buildIntMsg()</a></li>
							*/
							function encodeBody(contractName, arg0, arg1, arg2) returns (TvmCell);
							/**
							Functions are used to save state variables and quickly terminate execution of the smart contract. Exit codes are equal to zero and one for <code>tvm.exit</code> and <code>tvm.exit1</code> respectively.

							Example: 
							<code><pre>function g0(uint a) private {
								if (a == 0) {
									tvm.exit();
								}
								//...
							}
							
							function g1(uint a) private {
								if (a == 0) {
									tvm.exit1();
								}
								//...
							}</pre></code>
							*/
							function exit1();
							/**
							Functions are used to save state variables and quickly terminate execution of the smart contract. Exit codes are equal to zero and one for <code>tvm.exit</code> and <code>tvm.exit1</code> respectively.

							Example: 
							<code><pre>function g0(uint a) private {
								if (a == 0) {
									tvm.exit();
								}
								//...
							}
							
							function g1(uint a) private {
								if (a == 0) {
									tvm.exit1();
								}
								//...
							}</pre></code>
							*/
							function exit();
							/**
							*/
							function exit1();
							/**
							Generates <code>data</code> field of the <code>StateInit</code> (<a href="https://test.ton.org/tblkch.pdf">TBLKCH</a>> - 3.1.7.). Parameters are the same as in <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmbuildstateinit">tvm.buildStateInit()</a>
							<code><pre>// SimpleWallet.sol
								contract SimpleWallet {
									uint static value;
									// ...
								}
								
								// Usage
								TvmCell data = tvm.buildDataInit({
									contr: SimpleWallet,
									varInit: {mulRes: 12345},
									pubkey: 0x3f82435f2bd40915c28f56d3c2f07af4108931ae8bf1ca9403dcf77d96250827
								});
								TvmCell code = ...;
								TvmCell stateInit = tvm.buildStateInit({code: code, data: data});
								
								// Note, the code above can be simplified to:
								TvmCell stateInit = tvm.buildStateInit({
									code: code,
									contr: SimpleWallet,
									varInit: {mulRes: 12345},
									pubkey: 0x3f82435f2bd40915c28f56d3c2f07af4108931ae8bf1ca9403dcf77d96250827
								});</pre></code>
							*/
							function buildDataInit(uint256 pubkey, contractName, VarInit varInit);
							/**
							Function should be used only offchain and intended to be used only in debot contracts. Allows creating an external inbound message, that calls the func function of the contract on address destination with specified function arguments.

							Mandatory parameters that are used to form a src field that is used for debots:
							<li><code>callbackId</code> - identifier of the callback function.</li>
							<li><code>onErrorId</code> - identifier of the function that is called in case of an error.</li>
							<li><code>signBoxHandle</code> - handle of the sign box entity, that engine will use to sign the message.</li>

							These parameters are stored in addr_extern and placed to the src field of the message. Message is of type <a href="https://github.com/ton-blockchain/ton/blob/24dc184a2ea67f9c47042b4104bbb4d82289fac1/crypto/block/block.tlb#L127">ext_in_msg_info</a> and src address is of type <a href="https://github.com/ton-blockchain/ton/blob/24dc184a2ea67f9c47042b4104bbb4d82289fac1/crypto/block/block.tlb#L101">addr_extern</a> but stores special data:
							<li>callback id - 32 bits;</li>
							<li>on error id - 32 bits;</li>
							<li>abi version - 8 bits; Can be specified manually and contain full abi version in little endian half bytes (e.g. version = "2.3" -> abiVer: 0x32)</li>
							<li>header mask - 3 bits in such order: time, expire, pubkey;</li>
							<li>optional value signBoxHandle - 1 bit (whether value is present) + [32 bits];</li>
							<li>control flags byte - 8 bits. Currently used bits: 1 - override time (dengine will replace time value with current time) 2 - override exp (dengine will replace time value with actual expire value) 4 - async call (dengine must send message and don't wait for the result)</li>
							
							Other function parameters define fields of the message:
							<li><code>time</code> - message creation timestamp. Used for replay attack protection, encoded as 64 bit Unix time in milliseconds.</li>
							<li><code>expire</code> - Unix time (in seconds, 32 bit) after that message should not be processed by contract.</li>
							<li><code>pubkey</code> - public key from key pair used for signing the message body. This parameter is optional and can be omitted.</li>
							<li><code>sign</code> - constant bool flag that shows whether message should contain signature. If set to true, message is generated with signature field filled with zeroes. This parameter is optional and can be omitted (in this case is equal to false).</li>

							User can also attach stateInit to the message using <code>stateInit</code> parameter.

							Function throws an exception with code 64 if function is called with wrong parameters (pubkey is set and has value, but sign is false or omitted).

							Example:
							<code><pre>
								interface Foo {
									function bar(uint a, uint b) external pure;
								}
								
								contract Test {
								
									TvmCell public m_cell;
								
									function generate0() public {
										tvm.accept();
										address addr = address.makeAddrStd(0, 0x0123456789012345678901234567890123456789012345678901234567890123);
										m_cell = tvm.buildExtMsg({callbackId: 0, onErrorId: 0, dest: addr, time: 0x123, expire: 0x12345, call: {Foo.bar, 111, 88}});
									}
								
									function generate1() public {
										tvm.accept();
										optional(uint) pubkey;
										address addr = address.makeAddrStd(0, 0x0123456789012345678901234567890123456789012345678901234567890123);
										m_cell = tvm.buildExtMsg({callbackId: 0, onErrorId: 0, dest: addr, time: 0x123, expire: 0x12345, call: {Foo.bar, 111, 88}, pubkey: pubkey});
									}
								
									function generate2() public {
										tvm.accept();
										optional(uint) pubkey;
										pubkey.set(0x95c06aa743d1f9000dd64b75498f106af4b7e7444234d7de67ea26988f6181df);
										address addr = address.makeAddrStd(0, 0x0123456789012345678901234567890123456789012345678901234567890123);
										optional(uint32) signBox;
										signBox.set(0x12333112);
										m_cell = tvm.buildExtMsg({callbackId: 0, onErrorId: 0, dest: addr, time: 0x1771c58ef9a, expire: 0x600741e4, call: {Foo.bar, 111, 88}, pubkey: pubkey, sign: true, signBoxHandle: signBox});
									}
								
								}</pre></code>

							External inbound message can also be built and sent with construction similar to remote contract call. It requires suffix ".extMsg" and call options similar to <code>buildExtMsg</code> function call. Note: this type of call should be used only offchain in debot contracts.
							<code><pre>interface Foo {
								function bar(uint a, uint b) external pure;
							}
							
							contract Test {
							
								function test7() public {
									address addr = address.makeAddrStd(0, 0x0123456789012345678901234567890123456789012345678901234567890123);
									Foo(addr).bar{expire: 0x12345, time: 0x123}(123, 45).extMsg;
									optional(uint) pubkey;
									optional(uint32) signBox;
									Foo(addr).bar{expire: 0x12345, time: 0x123, pubkey: pubkey}(123, 45).extMsg;
									Foo(addr).bar{expire: 0x12345, time: 0x123, pubkey: pubkey, sign: true}(123, 45).extMsg;
									pubkey.set(0x95c06aa743d1f9000dd64b75498f106af4b7e7444234d7de67ea26988f6181df);
									Foo(addr).bar{expire: 0x12345, time: 0x123, pubkey: pubkey, sign: true}(123, 45).extMsg;
									Foo(addr).bar{expire: 0x12345, time: 0x123, sign: true, signBoxHandle: signBox}(123, 45).extMsg;
									Foo(addr).bar{expire: 0x12345, time: 0x123, sign: true, signBoxHandle: signBox, abiVer: 0x32, flags: 0x07}(123, 45).extMsg;
								}
							}</pre></code>
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
							Generates an internal outbound message that contains a function call. The result <code>TvmCell</code> can be used to send a message using <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmsendrawmsg">tvm.sendrawmsg()</a> If the <code>function</code> is <code>responsible</code> then <code>callbackFunction</code> parameter must be set.

							<code>dest</code>, <code>value</code> and <code>call</code> parameters are mandatory. Another parameters can be omitted. See <a href=https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#addresstransfer"><address>.transfer()</a> where these options and their default values are described.

							See also:
							<li>sample <a href="https://github.com/tonlabs/samples/blob/master/solidity/22_sender.sol">22_sender.sol</a></li>
							<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmencodebody">tvm.encodeBody()</a></li>
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
							Send the internal/external message <code>msg</code> with <code>flag</code>. It's a wrapper for opcode <code>SENDRAWMSG</code> (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.10). Internal message <code>msg</code> can be generated by <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmbuildintmsg">tvm.buildIntMsg()</a> Possible values of <code>flag</code> are described here: <a href=""><address>.transfer()</a>
							
							<strong>Note</strong>: make sure that <code>msg</code> has a correct format and follows the <a href="https://github.com/ton-blockchain/ton/blob/master/crypto/block/block.tlb">TL-B scheme</a> of <code>Message X</code>. For example:\
							<code><pre>TvmCell msg = ...
							tvm.sendrawmsg(msg, 2);</pre></code>

							If the function is called by external message and <code>msg</code> has a wrong format (for example, the field <code>init</code> of <code>Message X</code> is not valid) then the transaction will be replayed despite the usage of flag 2. It will happen because the transaction will fail at the action phase.
							*/
							function sendrawmsg(TvmCell msg, uint8 flag);
           
      }
    """)
  }


  val mathType: SolContract by lazy {
    contract("""
      contract ${internalise("Math")} {
           // todo varargs
							/**
							Returns the minimal (maximal) value of the passed arguments. <code>T</code> should be an integer or fixed point type
                @custom:no_validation
                @custom:typeArgument T:Number
							*/
							function min(T a, T varargs) returns (T);
							/**
							Returns the minimal (maximal) value of the passed arguments. <code>T</code> should be an integer or fixed point type
                @custom:no_validation
                @custom:typeArgument T:Number
							*/
							function max(T a, T varargs) returns (T);
							/**
							Returns minimal and maximal values of the passed arguments. <code>T</code> should be an integer or fixed point type

							Example:
							<code>(uint a, uint b) = math.minmax(20, 10); // (10, 20)</code>
              @custom:typeArgument T:Number
							*/
							function minmax(T a, T varargs) returns (T /*min*/, T /*max*/);
							/**
							Computes the absolute value of the given integer.

							Example:
							<code><pre>int a = math.abs(-4123); // 4123
							int b = -333;
							int c = math.abs(b); // 333</pre></code>
              @custom:typeArgument T:Number
							*/
							function abs(T val) returns (T);
							/**
							Computes the value modulo 2^power. Note that power should be a constant integer.

							Example:
							<code><pre>uint constant pow = 12;
							uint val = 12313;
							uint a = math.modpow2(val, 10);
							uint b = math.modpow2(val, pow);</pre></code>
							*/
							function modpow2(uint value, uint power) returns (uint);
							/**
							Returns result of the division of two integers. <code>T</code> should be an integer or fixed point type. The return value is rounded. <strong>ceiling</strong> and nearest modes are used for <code>divc</code> and <code>divr</code> respectively. See also: <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#division-and-rounding">Division and rounding</a>

							Example: 
							<code><pre>int c = math.divc(10, 3); // c = 4
							int c = math.divr(10, 3); // c = 3
								
							fixed32x2 a = 0.25;
							fixed32x2 res = math.divc(a, 2); // res == 0.13</pre></code>
              @custom:typeArgument T:Number
							*/
							function divc(T a, T b) returns (T);
							/**
							Returns result of the division of two integers. <code>T</code> should be an integer or fixed point type. The return value is rounded. <strong>ceiling</strong> and nearest modes are used for <code>divc</code> and <code>divr</code> respectively. See also: <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#division-and-rounding">Division and rounding</a>

							Example: 
							<code><pre>int c = math.divc(10, 3); // c = 4
							int c = math.divr(10, 3); // c = 3
								
							fixed32x2 a = 0.25;
							fixed32x2 res = math.divc(a, 2); // res == 0.13</pre></code>
              @custom:typeArgument T:Number
							*/
							function divr(T a, T b) returns (T);
							/**
							This instruction multiplies first two arguments, divides the result by third argument and returns the result and the remainder. Intermediate result is stored in the 514 bit buffer, and the final result is rounded to the floor.

							Example:
							<code><pre>uint a = 3;
							uint b = 2;
							uint c = 5;
							(uint d, uint r) = math.muldivmod(a, b, c); // (1, 1)
							int e = -1;
							int f = 3;
							int g = 2;
							(int h, int p) = math.muldivmod(e, f, g); // (-2, 1)</pre></code>
              @custom:typeArgument T:Int
							*/
							function muldivmod(T a, T b, T c) returns (T /*result*/, T /*remainder*/);
							/**
							Multiplies two values and then divides the result by a third value. <code>T</code> is integer type. The return value is rounded. <strong>floor</strong>, <strong>ceiling</strong> and <strong>nearest</strong> modes are used for <code>muldiv</code>, <code>muldivc</code> and <code>muldivr</code> respectively. See also: <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#division-and-rounding">Division and rounding</a>

							Example:
							<code><pre>uint res = math.muldiv(3, 7, 2); // res == 10
							uint res = math.muldivr(3, 7, 2); // res == 11
							uint res = math.muldivc(3, 7, 2); // res == 11</pre></code>
              @custom:typeArgument T:Int
							*/
							function muldiv(T a, T b, T c) returns (T);
							/**
							This instruction multiplies first two arguments, divides the result by third argument and returns the result and the remainder. Intermediate result is stored in the 514 bit buffer, and the final result is rounded to the floor.

							Example:
							<code><pre>uint a = 3;
							uint b = 2;
							uint c = 5;
							(uint d, uint r) = math.muldivmod(a, b, c); // (1, 1)
							int e = -1;
							int f = 3;
							int g = 2;
							(int h, int p) = math.muldivmod(e, f, g); // (-2, 1)</pre></code>
              @custom:typeArgument T:Int
							*/
							function muldivmod(T a, T b, T c) returns (T /*result*/, T /*remainder*/);
							/**
							Multiplies two values and then divides the result by a third value. <code>T</code> is integer type. The return value is rounded. <strong>floor</strong>, <strong>ceiling</strong> and <strong>nearest</strong> modes are used for <code>muldiv</code>, <code>muldivc</code> and <code>muldivr</code> respectively. See also: <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#division-and-rounding">Division and rounding</a>

							Example:
							<code><pre>uint res = math.muldiv(3, 7, 2); // res == 10
							uint res = math.muldivr(3, 7, 2); // res == 11
							uint res = math.muldivc(3, 7, 2); // res == 11</pre></code>
              @custom:typeArgument T:Int
							*/
							function muldivr(T a, T b, T c) returns (T);
							/**
							This instruction multiplies first two arguments, divides the result by third argument and returns the result and the remainder. Intermediate result is stored in the 514 bit buffer, and the final result is rounded to the floor.

							Example:
							<code><pre>uint a = 3;
							uint b = 2;
							uint c = 5;
							(uint d, uint r) = math.muldivmod(a, b, c); // (1, 1)
							int e = -1;
							int f = 3;
							int g = 2;
							(int h, int p) = math.muldivmod(e, f, g); // (-2, 1)</pre></code>
              @custom:typeArgument T:Int
							*/
							function muldivmod(T a, T b, T c) returns (T /*result*/, T /*remainder*/);
							/**
							Multiplies two values and then divides the result by a third value. <code>T</code> is integer type. The return value is rounded. <strong>floor</strong>, <strong>ceiling</strong> and <strong>nearest</strong> modes are used for <code>muldiv</code>, <code>muldivc</code> and <code>muldivr</code> respectively. See also: <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#division-and-rounding">Division and rounding</a>

							Example:
							<code><pre>uint res = math.muldiv(3, 7, 2); // res == 10
							uint res = math.muldivr(3, 7, 2); // res == 11
							uint res = math.muldivc(3, 7, 2); // res == 11</pre></code>
              @custom:typeArgument T:Int
							*/
							function muldivc(T a, T b, T c) returns (T);
							/**
							This function divides the first number by the second and returns the result and the remainder. Result is rounded to the floor. <code>T</code> must be an integer type.

							Example:
							<code><pre>uint a = 3;
							uint b = 2;
							(uint d, uint r) = math.divmod(a, b);
							int e = -1;
							int f = 3;
							(int h, int p) = math.divmod(e, f);</pre></code>
              @custom:typeArgument T:Int
							*/
							function divmod(T a, T b) returns (T /*result*/, T /*remainder*/);
							/**
							Returns the number in case of the sign of the argument value <code>val</code>:
							<li>-1 if <code>val</code> is negative;</li>
							<li>0 if <code>val</code> is zero;</li>
							<li>1 if <code>val</code> is positive.</li>

							Example:
							<code><pre>int8 sign = math.sign(-1333); // sign == -1
							int8 sign = math.sign(44); // sign == 1
							int8 sign = math.sign(0); // sign == 0</pre></code>
							*/
							function sign(int val) returns (int8);
           
      }
    """)
  }

  val blockType: SolType by lazy {
    contract("""
            contract ${internalise("Block")}{
                 address coinbase;
                 uint difficulty;
                 uint gasLimit;
                 uint number;
                 uint32 timestamp;
                 
                 function blockhash(uint blockNumber) returns (bytes32);
            }      
        """)
  }

  val globalType: SolContract by lazy {
    contract("""
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
causes a Panic error and thus state change reversion if the condition is not met - to be used for internal errors.

							*/
              function assert(bool condition);
							/**
							*/
              function bitSize(int x) private returns (uint16);
							/**
							*/
              function uBitSize(uint x) returns (uint16);
							/**
							In case of exception state variables of the contract are reverted to the state before <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmcommit">tvm.commit()</a> or to the state of the contract before it was called. Use error codes that are greater than 100 because other error codes can be <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#solidity-runtime-errors">reserved</a> <strong>Note</strong> if a nonconstant error code is passed as the function argument and the error code is less than 2 then the error code will be set to 100.
							
							<strong>require</strong> function can be used to check the condition and throw an exception if the condition is not met. The function takes condition and optional parameters: error code (unsigned integer) and the object of any type.
							
							Example:
							<code><pre>uint a = 5;

							require(a == 5); // ok
							require(a == 6); // throws an exception with code 100
							require(a == 6, 101); // throws an exception with code 101
							require(a == 6, 101, "a is not equal to six"); // throws an exception with code 101 and string
							require(a == 6, 101, a); // throws an exception with code 101 and number a</pre></code>
							*/
							function require(bool condition);
							/**
							In case of exception state variables of the contract are reverted to the state before <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmcommit">tvm.commit()</a> or to the state of the contract before it was called. Use error codes that are greater than 100 because other error codes can be <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#solidity-runtime-errors">reserved</a> <strong>Note</strong> if a nonconstant error code is passed as the function argument and the error code is less than 2 then the error code will be set to 100.
							
							<strong>require</strong> function can be used to check the condition and throw an exception if the condition is not met. The function takes condition and optional parameters: error code (unsigned integer) and the object of any type.
							
							Example:
							<code><pre>uint a = 5;

							require(a == 5); // ok
							require(a == 6); // throws an exception with code 100
							require(a == 6, 101); // throws an exception with code 101
							require(a == 6, 101, "a is not equal to six"); // throws an exception with code 101 and string
							require(a == 6, 101, a); // throws an exception with code 101 and number a</pre></code>
							*/
							function require(bool condition, string message);

              /**
                 require function can be used to check the condition and throw an exception if the condition is not met. The function takes condition and optional parameter: error code (unsigned integer).
              */
              function require(bool condition, uint errorCode);
              /**
                 require function can be used to check the condition and throw an exception if the condition is not met. The function takes condition and optional parameters: error code (unsigned integer) and the object of any type.
              */
              function require(bool condition, uint errorCode, Type exceptionArgument);
              
							/**
							In case of exception state variables of the contract are reverted to the state before <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmcommit">tvm.commit()</a> or to the state of the contract before it was called. Use error codes that are greater than 100 because other error codes can be <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#solidity-runtime-errors">reserved</a> <strong>Note</strong> if a nonconstant error code is passed as the function argument and the error code is less than 2 then the error code will be set to 100.
							
							<strong>revert</strong> function can be used to throw exceptions. The function takes an optional error code (unsigned integer) and the object of any type.
							
							Example:
							<code><pre>uint a = 5;
								revert(); // throw exception 100
								revert(101); // throw exception 101
								revert(102, "We have a some problem"); // throw exception 102 and string
								revert(101, a); // throw exception 101 and number a</pre></code>
							*/
							function revert();
							/**
                revert function can be used to throw exceptions. The function takes an optional error code (unsigned integer).
							*/
							function revert(uint errorCode);
							/**
                revert function can be used to throw exceptions. The function takes an optional error code (unsigned integer) and the object of any type.
							*/
							function revert(uint errorCode, Type exceptionArgument);
							/**
							In case of exception state variables of the contract are reverted to the state before <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmcommit">tvm.commit()</a> or to the state of the contract before it was called. Use error codes that are greater than 100 because other error codes can be <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#solidity-runtime-errors">reserved</a> <strong>Note</strong> if a nonconstant error code is passed as the function argument and the error code is less than 2 then the error code will be set to 100.
							
							<strong>revert</strong> function can be used to throw exceptions. The function takes an optional error code (unsigned integer) and the object of any type.
							
							Example:keccak256
							<code><pre>uint a = 5;
								revert(); // throw exception 100
								revert(101); // throw exception 101
								revert(102, "We have a some problem"); // throw exception 102 and string
								revert(101, a); // throw exception 101 and number a</pre></code>
							*/
							function revert(string) {}
							/**
							This returns the <code>bytes32</code> keccak256 hash of the bytes.
							*/
							function keccak256(bytes memory input) returns (bytes32);
							/**
							The <strong>keccak256 (SHA-3 family)</strong> algorithm computes the hash of an <code>input</code> to a fixed length <code>output</code>. The input can be a variable length string or number, but the result will always be a fixed <strong>bytes32</strong> data type. It is a one-way cryptographic hash function, which cannot be decoded in reverse.
							*/
							function sha3(bytes memory input) returns (bytes32);
							/**
							<li>1. Computes the SHA-256 hash. If the bit length of <code>slice</code> is not divisible by eight, throws a cell underflow <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvm-exception-codes">exception</a> References of <code>slice</code> are not used to compute the hash. Only data bits located in the root cell of <code>slice</code> are used.</li>
							<li>2. Computes the SHA-256 hash only for the first 127 bytes. If <code>bytes.length > 127</code> then <code>b[128], b[129], b[130] ...</code> elements are ignored.</li>
							<li>3. Same as for <code>bytes</code>: only the first 127 bytes are taken into account.</li>

							See also <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmhash">tvm.hash()</a> to compute representation hash of the whole tree of cells.
							*/
							function sha256(bytes memory input) returns (bytes32);
							/**
							This returns the <code>bytes20</code> ripemd160 hash of the bytes.
							*/
							function ripemd160(bytes memory input) returns (bytes20);
							/**
							*/
							function ecrecover(bytes32 hash, uint8 v, bytes32 r, bytes32 s) returns (address);
							/**
							Add x to y, and then divides by k. x + y will not overflow.
							*/
							function addmod(uint x, uint y, uint k) returns (uint);
							/**
							Multiply x with y, and then divides by k. x * y will not overflow.
							*/
							function mulmod(uint x, uint y, uint k) returns (uint);
							/**
							Creates and sends the message that carries all the remaining balance of the current smart contract and destroys the current account.

							See example of how to use the <code>selfdestruct</code> function:
							<li><a href="https://github.com/tonlabs/samples/blob/master/solidity/8_Kamikaze.sol">Kamikaze</a></li>
							*/
							function selfdestruct(address recipient);

              /**
              Returns worth of <b>gas</b> in workchain <b>wid</b>. Throws an exception if <b>wid</b> is not equal to <code>0</code> or <code>-1</code>. If <code>wid</code> is omitted than used the contract's <code>wid</code>
              */
              function gasToValue(uint128 gas) returns (uint128 value)
              /**
              Returns worth of <b>gas</b> in workchain <b>wid</b>. Throws an exception if <b>wid</b> is not equal to <code>0</code> or <code>-1</code>. If <code>wid</code> is omitted than used the contract's <code>wid</code>
              */
              function gasToValue(uint128 gas, int8 wid) returns (uint128 value)
              
              /**
              Counts how much <b>gas</b> could be bought on <b>value</b> nanotons in workchain <b>wid</b>. Throws an exception if <b>wid</b> is not equal to <code>0</code> or <code>-1</code>. If <code>wid</code> is omitted than used the contract's <code>wid</code>
              */
              function valueToGas(uint128 value) returns (uint128 gas)
              /**
              Counts how much <b>gas</b> could be bought on <b>value</b> nanotons in workchain <b>wid</b>. Throws an exception if <b>wid</b> is not equal to <code>0</code> or <code>-1</code>. If <code>wid</code> is omitted than used the contract's <code>wid</code>
              */
              function valueToGas(uint128 value, int8 wid) returns (uint128 gas)              
          
              /**
              Dumps <code>log</code> string. This function is a wrapper for TVM instructions <code>PRINTSTR</code> (for constant literal strings shorter than 16 symbols) and <code>STRDUMP</code> (for other strings). 
              <code>logtvm</code> is an alias for <code>tvm.log(string)</code>. Example:
              <code>
              tvm.log("Hello, world!");
              logtvm("99_Bottles");
              
              string s = "Some_text";
              tvm.log(s);
              </code>
              <b>Note:</b> For long strings dumps only the first 127 symbols.
              */
              function logtvm(string log);
              
              /**
              @custom:no_validation
              */
              function format(string template, Type varargs) returns (string);
              /**
              */
              function stoi(string inputStr) returns (optional(int) /*result*/);

      }
    """)
  }


  private fun contract(@Language("T-Sol") contractBody: String) =
    SolContract(psiFactory.createContract(contractBody), true)

}
