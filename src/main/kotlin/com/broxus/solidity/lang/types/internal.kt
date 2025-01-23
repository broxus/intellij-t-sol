package com.broxus.solidity.lang.types

import com.broxus.solidity.childrenOfType
import com.broxus.solidity.ide.hints.VERSION_TAG
import com.broxus.solidity.ide.hints.tagComments
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.lang.psi.SolNamedElement
import com.broxus.solidity.lang.psi.SolPragmaDirective
import com.broxus.solidity.lang.psi.SolPsiFactory
import com.broxus.solidity.lang.resolve.SolResolver
import com.github.yuchi.semver.Range
import com.github.yuchi.semver.SemVer
import com.github.yuchi.semver.Version
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import kotlin.reflect.KProperty1

class SolInternalTypeFactory(project: Project) {

  private val psiFactory: SolPsiFactory = SolPsiFactory(project)

  companion object {
    const val varargsId = "varargs"
    fun of(project: Project): SolInternalTypeFactory {
      return ServiceManager.getService(project, SolInternalTypeFactory::class.java)
    }
  }

  private val registry: Map<String, SolType> by lazy {
    listOf(
            abiType,
            addressType,
            arrayType,
            globalType,
            msgType,
            txType,
            blockType,
      blsType,
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

  val everBuiltinTypes: Map<String, SolNamedElement> by lazy {
    listOf(
      tvmCell.ref,
      tvmSlice.ref,
      tvmBuilder.ref,
      stringBuilderType.ref,
      extraCurrencyCollection,
    ).associateBy { it.name!! }
  }

  fun builtinByName(name: String): SolNamedElement? = everBuiltinTypes[name]

  fun builtinTypeByName(name: String, context: List<SolType>): SolType? = when (name) {
    "Number" -> SolNumericType
    "Int" -> SolInteger.MAX_INT_TYPE
    "Type" -> SolTypeType
//    "TypeSequence" -> SolTypeSequence(context)
    else -> null
  }

  val tvmCell: SolContract by lazy {
    contract("""
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
              
               // toSlice is the final function here!
          }
        """, "toSlice", "TvmCell c; c.")
  }

  val tvmSlice: SolContract by lazy {
    contract("""
          contract TvmSlice {
              /**
              Checks whether the <code>TvmSlice</code> is empty (i.e., contains no data bits and no cell references).
              */              
              function empty() returns (bool);
              /**
              Checks whether the <code>TvmSlice</code> contains no data bits.
              @custom:version min=0.75.0
              */              
              function bitEmpty() returns (bool);
              /**
              Checks whether the <code>TvmSlice</code> contains no cell references.
              @custom:version min=0.75.0
              */              
              function refEmpty() returns (bool);
              
              /**
              Checks whether <code>prefix</code> is a prefix of <code>TvmSlice</code>
              @custom:version min=0.75.0
              */              
              function startsWith(TvmSlice prefix) returns (bool);
              
              /**
              Checks whether the first bit of <code>TvmSlice</code> is a one.
              @custom:version min=0.75.0
              */              
              function startsWithOne() returns (bool);              
              /**
              Converts an ordinary or exotic cell into a <code>TvmSlice</code>, as if it were an ordinary cell. A flag is returned indicating whether <code>TvmCell</code> is exotic. If that be the case, its type can later be deserialized from the first eight bits of <code>TvmSlice</code>.
              
              Example:
              
              <pre><code>TvmCell cellProof = ...;
              TvmBuilder b;
              b.store(
                  uint8(3), // type of MerkleProof exotic cell
                  tvm.hash(cellProof),
                  cellProof.depth(),
                  cellProof
              );
              
              {
                  // convert builder to exotic cell
                  TvmCell merkleProof = b.toExoticCell();
                  (TvmSlice s, bool isExotic) = merkleProof.exoticToSlice();
                  // isExotic == true
                  uint8 flag = s.load(uint8); // flag == 3
              }
              
              {
                  // convert builder to ordinary cell
                  TvmCell cell = b.toCell();
                  (TvmSlice s, bool isExotic) = cell.exoticToSlice();
                  // isExotic == false
                  uint8 flag = s.load(uint8); // flag == 3
              }</code></pre>
              @custom:version min=0.72.0
              */
              function exoticToSlice() returns (TvmSlice, bool);
              /**
              Loads an exotic cell and returns an ordinary cell. If the cell is already ordinary, does nothing. If it cannot be loaded, throws an exception. It is wrapper for opcode XLOAD
              Example:
              
              <pre><code>TvmCell cellProof = ...;
              TvmBuilder b;
              b.store(
                  uint8(3), // type of MerkleProof exotic cell
                  tvm.hash(cellProof),
                  cellProof.depth(),
                  cellProof
              );
              
              TvmCell cell = merkleProof.loadExoticCell(); // cell == cellProof</code></pre>
              @custom:version min=0.72.0
              */  
              function loadExoticCell() returns (TvmCell);
              /**
              Loads an exotic cell and returns an ordinary cell. If the cell is already ordinary, does nothing. If it cannot be loaded, does not throw exception and ok is equal to false. It is wrapper for opcode XLOADQ.
              Example:
              
              <pre><code>TvmCell cellProof = ...;
              TvmBuilder b;
              b.store(
                  uint8(3), // type of MerkleProof exotic cell
                  tvm.hash(cellProof),
                  cellProof.depth(),
                  cellProof
              );
              
              (TvmCell cell, bool ok) = merkleProof.loadExoticCellQ();
              // cell == cellProof
              // ok == true</code></pre>
              @custom:version min=0.72.0
              */  
              function loadExoticCellQ() returns (TvmCell cell, bool ok);
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
              
              <pre><code><pre>TvmSlice slice = ...;
              (uint8 a, uint16 b) = slice.decode(uint8, uint16);
              (uint16 num0, uint32 num1, address addr) = slice.decode(uint16, uint32, address);</code></pre>
               @custom:no_validation
               @custom:deprecated
              */              
              function decode(Type varargs) returns (TypeSequence);
              /**
              Sequentially decodes values of the specified types from the <code>TvmSlice</code>. Supported types: <code>uintN</code>, <code>intN</code>, <code>bytesN</code>, <code>bool</code>, <code>ufixedMxN</code>, <code>fixedMxN</code>, <code>address</code>, <code>contract</code>, <code>TvmCell</code>, <code>bytes</code>, <code>string</code>, <code>mapping</code>, <code>ExtraCurrencyCollection</code>, <code>array</code>, <code>optional</code> and <code>struct</code>. Example:
              
              <pre><code><pre>TvmSlice slice = ...;
              (uint8 a, uint16 b) = slice.decode(uint8, uint16);
              (uint16 num0, uint32 num1, address addr) = slice.decode(uint16, uint32, address);</code></pre>
               @custom:no_validation
               @custom:version min=0.70.0
              */              
              function load(Type varargs) returns (TypeSequence);
              /**
              Sequentially decodes values of the specified types from the <code>TvmSlice</code> if the <code>TvmSlice</code> holds sufficient data for all specified types. Otherwise, returns null.
              
              Supported types: <code>uintN</code>, <code>intN</code>, <code>bytesN</code>, <code>bool</code>, <code>ufixedMxN</code>, <code>fixedMxN</code>, <code>address</code>, <code>contract</code>, <code>TvmCell</code>, <code>bytes</code>, <code>string</code>, <code>mapping</code>, <code>ExtraCurrencyCollection</code>, and <code>array</code>.
              
              <pre><code>TvmSlice slice = ...;
              optional(uint) a = slice.decodeQ(uint);
              optional(uint8, uint16) b = slice.decodeQ(uint8, uint16);</code></pre>
              @custom:no_validation
              @custom:deprecated
              */              
              function decodeQ(Type varargs) returns (optional(TypeSequence)); 
                            /**
              Sequentially decodes values of the specified types from the <code>TvmSlice</code> if the <code>TvmSlice</code> holds sufficient data for all specified types. Otherwise, returns null.
              
              Supported types: <code>uintN</code>, <code>intN</code>, <code>bytesN</code>, <code>bool</code>, <code>ufixedMxN</code>, <code>fixedMxN</code>, <code>address</code>, <code>contract</code>, <code>TvmCell</code>, <code>bytes</code>, <code>string</code>, <code>mapping</code>, <code>ExtraCurrencyCollection</code>, and <code>array</code>.
              
              <code><pre>TvmSlice slice = ...;
              optional(uint) a = slice.decodeQ(uint);
              optional(uint8, uint16) b = slice.decodeQ(uint8, uint16);</pre></code>
              @custom:no_validation
              @custom:version min=0.70.0
              */              
              function loadQ(Type varargs) returns (optional(TypeSequence)); 
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
              @custom:deprecated
              */              
              function loadSigned(uint16 bitSize) returns (int); 
              /**
              Loads a signed integer with the given bitSize from the <code>TvmSlice</code>.
              @custom:version min=0.70.0              
              */              
              function loadInt(uint16 bitSize) returns (int); 
              /**
              Loads an unsigned integer with the given bitSize from the <code>TvmSlice</code>.
              @custom:deprecated
              */              
              function loadUnsigned(uint16 bitSize) returns (uint); 
              /**
              Loads an unsigned integer with the given bitSize from the <code>TvmSlice</code>.
              @custom:version min=0.70.0              
              */              
              function loadUint(uint16 bitSize) returns (uint); 
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
              @custom:deprecated
              */              
              function decodeFunctionParams(Type functionOrContract) returns (TypeRet);
              /**
              Decodes parameters of the function or constructor (if contract type is provided). This function is usually used in <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#onbounce">onBounce</a> function.
              @custom:no_validation
              @custom:typeArgument Type:FunctionOrContract, TypeRet:DecodedElement
              @custom:version min=0.70.0              
              */              
              function loadFunctionParams(Type functionOrContract) returns (TypeRet);              
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
              @custom:deprecated              
              */              
              function decodeStateVars(Type contractName) returns (uint256 /*pubkey*/, uint64 /*timestamp*/, bool /*constructorFlag*/, Type1 /*var1*/, Type2 /*var2*/); 
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
              @custom:version min=0.70.0              
              */              
              function loadStateVars(Type contractName) returns (uint256 /*pubkey*/, uint64 /*timestamp*/, bool /*constructorFlag*/, Type1 /*var1*/, Type2 /*var2*/); 
              /**
              Skips the first <code>length</code> bits and <code>refs</code> references from the <code>TvmSlice</code>.
              */              
              function skip(uint10 length);
              /**
              Skips the first <code>length</code> bits and <code>refs</code> references from the <code>TvmSlice</code>.
              @custom:version max=0.71.0
              */              
              function skip(uint10 length, uint2 refs);
              /**
              Skips the first <code>length</code> bits and <code>refs</code> references from the <code>TvmSlice</code>.
              @custom:version min=0.72.0              
              */              
              function skip(uint10 length, uint3 refs);
              
              /**
               Returns the count <code>n</code> of leading zero bits in <code>TvmSlice</code>, and removes these bits from <code>TvmSlice</code>.
              @custom:version min=0.68.0              
              */              
              function loadZeroes() returns (uint10 n);
              /**
               Returns the count <code>n</code> of leading one bits in <code>TvmSlice</code>, and removes these bits from <code>TvmSlice</code>.
              @custom:version min=0.68.0              
              */              
              function loadOnes() returns (uint10 n);
              /**
              Returns the count <code>n</code> of leading bits equal to <code>0 ≤ value ≤ 1</code> in <code>TvmSlice</code>, and removes these bits from <code>TvmSlice</code>.
              @custom:version min=0.68.0              
              */              
              function loadSame() returns (uint10 n);
              /**
              @custom:version min=0.70.0              
              */              
              function loadIntQ(uint9 bitSize) returns (optional(int));
              /**
              @custom:version min=0.70.0              
              */              
              function loadUintQ(uint9 bitSize) returns (optional(uint));
              /**
              @custom:version min=0.70.0              
              */              
              function loadSliceQ(uint10 bits) returns (optional(TvmSlice));
              /**
              @custom:version min=0.70.0              
              */              
              function loadSliceQ(uint10 bits, uint2 refs) returns (optional(TvmSlice));
              /**
              @custom:version min=0.70.0              
              */              
              function loadIntLE2() returns (int16);
              /**
              @custom:version min=0.70.0              
              */              
              function loadIntLE4() returns (int32);
              /**
              @custom:version min=0.70.0              
              */              
              function loadIntLE8() returns (int64);
              /**
              @custom:version min=0.70.0              
              */              
              function loadUintLE2() returns (uint16);
              /**
              @custom:version min=0.70.0              
              */              
              function loadUintLE4() returns (uint32);
              /**
              @custom:version min=0.70.0              
              */              
              function loadUintLE8() returns (uint64);
              /**
              @custom:version min=0.70.0              
              */              
              function loadIntLE4Q() returns (optional(int32));
              /**
              @custom:version min=0.70.0              
              */              
              function loadIntLE8Q() returns (optional(int64));
              /**
              @custom:version min=0.70.0              
              */              
              function loadUintLE4Q() returns (optional(uint32));
              /**
              @custom:version min=0.70.0              
              */              
              function loadUintLE8Q() returns (optional(uint64));
              
              /**
              @custom:version min=0.70.0              
              
              */              
              function preload(Type varargs) returns (TypeSequence);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadQ(Type varargs) returns (optional(TypeSequence));
              /**
              @custom:version min=0.70.0              
              */              
              function preloadRef() returns (TvmCell);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadInt(uint9 bitSize) returns (int);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadIntQ(uint9 bitSize) returns (optional(int));
              /**
              @custom:version min=0.70.0              
              */              
              function preloadUint(uint9 bitSize) returns (uint);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadUintQ(uint9 bitSize) returns (optional(uint));
              /**
              @custom:version min=0.70.0              
              */              
              function preloadSlice(uint10 bits) returns (TvmSlice);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadSlice(uint10 bits, uint refs) returns (TvmSlice);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadSliceQ(uint10 bits) returns (optional(TvmSlice));
              /**
              @custom:version min=0.70.0              
              */              
              function preloadSliceQ(uint10 bits, uint4 refs) returns (optional(TvmSlice));
              /**
              @custom:version min=0.70.0              
              */              
              function preloadIntLE4() returns (int32);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadIntLE8() returns (int64);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadUintLE4() returns (uint32);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadUintLE8() returns (uint64);
              /**
              @custom:version min=0.70.0              
              */              
              function preloadIntLE4Q() returns (optional(int32));
              /**
              @custom:version min=0.70.0              
              */              
              function preloadIntLE8Q() returns (optional(int64));
              /**
              @custom:version min=0.70.0              
              */              
              function preloadUintLE4Q() returns (optional(uint32));
              /**
              @custom:version min=0.70.0              
              */              
              function preloadUintLE8Q() returns (optional(uint64));
               // preloadUintLE8Q is the final function here!
          }
        """, "preloadUintLE8Q", "TvmSlice s; s.")
  }

  val tvmBuilder: SolContract by lazy {
    contract("""
          contract TvmBuilder {
              /**
              Creates an exotic cell from TvmBuilder. It is wrapper for opcodes TRUE ENDXC.
              
              Examples:
              
              <pre><code>TvmCell cellProof = getCell();
              TvmBuilder b;
              b.store(
                  uint8(3), // type of MerkleProof exotic cell
                  tvm.hash(cellProof),
                  cellProof.depth(),
                  cellProof
              );
              TvmCell merkleProof = b.toExoticCell();</code></pre>
              @custom:version min=0.72.0
              */  
              function toExoticCell() returns (TvmCell);
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
              builder.store(a, b, uint(33));</pre></code> 
              @custom:no_validation
               @custom:typeArgument Type
							*/
							function store(Type varargs); 
              /**
              Same as <TvmBuilder>.store() but returns the success flag. It does not throw exceptions.
              
              Supported types:
              <ul>
              <li>uintN/intN/bytesN</li>
              <li>bool</li>
              <li>ufixedMxN/fixedMxN</li>
              <li>address/contract</li>
              <li>TvmCell/bytes/string</li>
              <li>TvmSlice/TvmBuilder</li>
              </ul>
              @custom:version min=0.76.0             
              @custom:no_validation
               @custom:typeArgument Type
              */
              function storeQ(Type value) returns (bool /*ok*/);
							/**
Stores <code>n</code> binary ones into the <code>TvmBuilder</code>.
							*/
							function storeOnes(uint n); 
							/**
Stores <code>n</code> binary zeroes into the <code>TvmBuilder</code>.
              @custom:version min=0.64.0
							*/
							function storeZeroes(uint n); 
							/**
              Stores <code>n</code> binary zeroes into the <code>TvmBuilder</code>.
              @custom:version max=0.63.0
							*/
							function storeZeros(uint n); 
							/**
Stores a signed integer value with given bitSize in the <code>TvmBuilder</code>.
@custom:deprecated
							*/
							function storeSigned(int256 value, uint16 bitSize);
 							/**
Stores a signed integer value with given bitSize in the <code>TvmBuilder</code>.
              @custom:version min=0.70.0              
							*/
							function storeInt(int256 value, uint16 bitSize); 
							/**
Stores an unsigned integer value with given bitSize in the <code>TvmBuilder</code>.
@custom:deprecated
							*/
							function storeUnsigned(uint256 value, uint16 bitSize); 
							/**
Stores an unsigned integer value with given bitSize in the <code>TvmBuilder</code>.
              @custom:version min=0.70.0              
							*/
							function storeUInt(uint256 value, uint16 bitSize); 
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
							/**
              @custom:version min=0.68.0
              */
							function storeSame(uint10 n, uint1 value);
 							/**
              @custom:version min=0.70.0
							*/
							function storeIntLE2(int16 value); 
 							/**
              @custom:version min=0.70.0
							*/
							function storeIntLE4(int32 value); 
 							/**
              @custom:version min=0.70.0
							*/
							function storeIntLE8(int64 value); 
 							/**
              @custom:version min=0.70.0
							*/
							function storeUintLE2(uint16 value); 
 							/**
              @custom:version min=0.70.0
							*/
							function storeUintLE4(uint32 value); 
 							/**
              @custom:version min=0.70.0
							*/
							function storeUintLE8(uint64 value); 

              // storeUintLE8 is the final function here!
 
            }
          """, "storeUintLE8", "TvmBuilder b; b.")
  }

  val extraCurrencyCollection: SolContractDefinition by lazy {
      psiFactory.createContract("""
            /**
               @custom:version max=0.71.0
            */
            contract ExtraCurrencyCollection {
            }
          """)
    }


  val msgType: SolContract by lazy {
    contract("""
      contract ${internalise("Msg")} {
          /**
            Returns the whole message.
          */
          TvmCell public data;
          /**
          Returns the payload (message body) of an inbound message.
          */
          TvmSlice body;
          uint public gas;
          address public sender;
          
          /**
          Returns:
          <ul>
          <li>Balance of the inbound message in nanoevers for internal message.</li>
          <li>0 for external message.</li>
          <li>Undefined value for tick/tock transaction.</li>
          </ul>
          @custom:version max=0.72.0
          */
          uint128 public value;
          /**
          Returns:
          <ul>
          <li>Balance of the inbound message in nanoevers for internal message.</li>
          <li>0 for external message.</li>
          <li>Undefined value for tick/tock transaction.</li>
          </ul>
          @custom:version min=0.73.0
          */
          varUint16 public value;
          
          /**
          @custom:version max=0.71.0
          */
          ExtraCurrencyCollection currencies;
          /**
          @custom:version min=0.72.0
          */
          mapping(uint32 => varUint32) currencies;
          
          uint32 createdAt;
          
          TvmSlice data;
          
          bool hasStateInit; 
          
          /**
          Returns:
          <ul>
          <li>the forward fee for the internal inbound message.</li>
          <li>0 for the external inbound message.</li>
          </ul>
          @custom:version min=0.71.0              
          */
          varUint16 forwardFee;
                    
          /**
          Returns:
          <ul>          
          <li>the field import_fee for external inbound message. Note: field import_fee is set offchain by user as they want and does not reflect the real import fee of the message.</li>
          <li>0 for the internal inbound message.</li>
          </ul>
          @custom:version min=0.71.0              
          */
          varUint16 importFee;
          
          /**
Returns public key that is used to check the message signature. If the message isn't signed then it's equal to <code>0</code>. See also: <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#contract-execution">Contract execution</a>,<a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#pragma-abiheader">pragma AbiHeader</a>.
          */
          function pubkey() returns (uint256);
         // pubkey is the final function here!
          
      }
    """, "pubkey", "msg.")
  }

  val txType: SolContract by lazy {
    contract("""
      contract ${internalise("Tx")} {
          uint public gasprice;
          address public origin;
          
          /**
          @custom:version min=0.67.0
          */
          uint64 public logicaltime;
           
          uint120 public storageFee; 
          
         /**
            @custom:version max=0.69.0
          */
          uint64 public timestamp;
          
      }
    """, "storageFee", "tx.")
  }

  val quietType: SolContract by lazy {
    contract(
      """
        /**
          @custom:typeArgument T2=T0
          */
          contract ${internalise("Quiet")} {
          
           /**
          Checks whether <code><T></code> is <code>NaN</code>. <code>T</code> is <code>qintN</code>, <code>quintN</code> or <code>qbool</code>. Example:
          
<pre><code>          function checkOverflow(quint32 a, quint32 b) private pure returns(bool) {
              quint32 s = a + b;
              return s.isNaN();</code></pre>
          }
          @custom:version min=0.74.0
*/
          function isNaN() returns (bool);

           /**
          Returns "non-quiet" integer. If <code><T></code> is <code>NaN</code>, then throws an exception. <code>T</code> is <code>qintN</code>, <code>quintN</code> or <code>qbool</code>. Example:
          
<pre><code>          function f(quint32 a, quint32 b) private pure {
              quint32 s = a + b;
              if (!s.isNaN()) {
                  uint32 ss = s.get(); 
                  // ...
              }
          }</code></pre>
          @custom:version min=0.74.0
          */
          function get() returns (T2)
           /**
          Returns "non-quiet" integer. If <code><T></code> is <code>NaN</code>, then returns default. <code>T</code> is <code>qintN</code>, <code>quintN</code> or <code>qbool</code>. Example:
<pre><code>          function f(quint32 a, quint32 b) private pure {
              quint32 s = a + b;
              uint32 ss = s.getOr(42); // ss is equal to `a + b` or 42
              // ... 
          }</code></pre>
          @custom:version min=0.74.0
          */
          function getOr(T2 default) returns (T2)
          
           /**
          Returns "non-quiet" integer. If <code><T></code> is <code>NaN</code>, then returns default value. <code>T</code> is <code>qintN</code>, <code>quintN</code> or <code>qbool</code>. Example:
          
<pre><code>          function f(quint32 a, quint32 b) private pure {
              quint32 s = a + b;
              uint32 ss = s.getOrDefault(); // ss is equal to `a + b` or 0
              // ... 
          }</code></pre>
          @custom:version min=0.74.0
          */
          function getOrDefault() returns (T2)
           /**
          Returns optional integer. If <code><T></code> is <code>NaN</code>, then returns <code>null</code>. <code>T</code> is <code>qintN</code>, <code>quintN</code> or <code>qbool</code>. Example:
          
          <pre><code>function f(quint32 a, quint32 b) private pure {
              quint32 s = a + b;
              optional(uint32) ss = s.toOptional(); // ss is equal to `a + b` or null
              // ... 
          }</code></pre>
          @custom:version min=0.74.0
          */
          function toOptional() returns (optional(T2))
          }
        """, "", ""
    )
  }

  val integerType: SolContract by lazy {
      contract("""
        contract ${internalise("Integer")} {
                  /**
          Convert <code><Integer></code> to <code>T</code> type. <code>T</code> is integer type. Type of <code><Integer></code> and <code>T</code> must have same sign or bit-size. Never throws an exception. For example:
          
          <pre><code>uint8 a = 255;
          uint4 b = a.cast(uint4); // b == 15
          
          uint8 a = 255;
          int8 b = a.cast(int8); // b == -1
          
          uint8 a = 255;
          int4 b = a.cast(uint4).cast(int4); // b == -1
          
          uint8 a = 255;
          // int4 b = a.cast(int4); // compilation fail</code></pre>
          Note: conversion via <code>T(x)</code> throws an exception if <code>x</code> does not fit into <code>T</code> type. For example:
          
          <pre><code>uint8 a = 10;
          uint4 b = uint4(a); // OK, a == 10
          
          uint8 a = 100;
          uint4 b = uint4(a); // throws an exception because type(uint4).max == 15
          
          int8 a = -1;
          uint8 b = uint8(a); // throws an exception because type(uint8).min == 0</code></pre>
          @custom:typeArgument T:Int
          @custom:version min=0.73.0
          */
          function cast(T) returns (T);
        }
        """, "cast", "address abc; abc.")
  }

  val addressType: SolContract by lazy {
    contract("""
      contract ${internalise("Address")} {
          int8 public wid;
          
          uint public value;
          
          /**
          Returns balance of the current contract account in nanoevers.
          @custom:version max=0.72.0
          */
          uint128 public balance;

          /**
          Returns balance of the current contract account in nanoevers.
          @custom:version min=0.73.0
          */
          varUint16 public balance;
          
          /**
          Returns currencies on the balance of the current contract account.
          @custom:version max=0.71.0
          */
          ExtraCurrencyCollection public currencies;
          /**
          Returns currencies on the balance of the current contract account.
          @custom:version min=0.72.0
          */
          mapping(uint32 => varUint32) public currencies;
          
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
    @custom:version max=0.72.0
*/
              function transfer(uint128 value, bool bounce, uint16 flag, TvmCell body, ExtraCurrencyCollection currencies, TvmCell stateInit);
              							/**
		Sends an internal outbound message to the address. Function parameters:
		
<li><code>value</code> (<code>varuint16</code>) - amount of nanotons sent attached to the message. Note: the sent value is
withdrawn from the contract's balance even if the contract has been called by internal inbound message.</li>
<li><code>currencies</code> (<code>mapping(uint32 => varuint32)</code>) - additional currencies attached to the message. Defaults to
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
    @custom:version min=0.73.0    
*/
              function transfer(varUint16 value, bool bounce, uint16 flag, TvmCell body, mapping(uint32 => varuint32) currencies, TvmCell stateInit);
							/**      
Constructs an <code>address</code> of type <strong>addr_std</strong> with given workchain id wid and value <strong>address_value</strong>.
							*/
							function makeAddrStd(int8 wid, uint _address) returns (address);
							/**
Constructs an <code>address</code> of type <strong>addr_none</strong>.
              @custom:version max=0.72.0
							*/
							function makeAddrNone() returns (address);
							/**
Constructs an <code>address</code> of type <strong>addr_extern</strong> with given value with <strong>bitCnt</strong> bit length.
							*/
							function makeAddrExtern() returns (address);
							/**
Returns type of the <code>addr_none</code>: 0 - <strong>addr_none</strong> 1 - <strong>addr_extern</strong> 2 - <strong>addr_std</strong>
@custom:version max=0.76.0       
							*/
							function getType() returns (uint8);
							/**
Returns type of the <code>addr_none</code>: 0 - <strong>addr_none</strong> 1 - <strong>addr_extern</strong> 2 - <strong>addr_std</strong>
@custom:version min=0.77.0
							*/
							function getType() returns (uint4);
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
               @custom:version max=0.70.0              
							*/
							function unpack() returns (int8 /*wid*/, uint256 /*value*/);
							/**
Parses <code>address</code> containing a valid <code>MsgAddressInt</code> (<code>addr_std</code>), applies rewriting from the anycast (if present) to the same-length prefix of the address, and returns both the workchain <code>wid</code> and the 256-bit address <code>value</code>. If the address <code>value</code> is not 256-bit, or if <code>address</code> is not a valid serialization of <code>MsgAddressInt</code>, throws a cell deserialization <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvm-exception-codes">exception</a> .

It's wrapper for opcode <code>REWRITESTDADDR</code>.

Example:

<code>(int8 wid, uint addr) = address(this).unpack();</code>
 @custom:no_validation
               @custom:version min=0.71.0              
							*/
							function unpack() returns (int32 /*wid*/, uint256 /*value*/);
                            // unpack is the final function here!
      }
    """, "unpack", "address abc; abc.")

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
                                function prev(KeyType key) returns (optional(KeyType, ValueType));
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
                                /**
     Deletes the key from the mapping map and returns an optional with the corresponding value. Returns an empty optional if the key does not exist.
                               @custom:version min=0.71.0              
                  */
                                function getDel(KeyType key) returns (optional(ValueType));
                                 // getDel is the final function here!
    
          }
        """,
            "getDel",
            "mapping(uint => string) stakes; stakes.")
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
           // pop is the final function here!
      }
    """, "pop", "byte[] asdf; asdf.")
  }

  val optionalType: SolContract by lazy {
    contract("""
      /**
       The template optional type manages an optional contained value, i.e. a value that may or may not be present.
       @custom:typeArgument T=T0
      */
      contract ${internalise("Optional")} {

							/**
Checks whether the <code>optional</code> contains a value.
							*/
							function hasValue() returns (bool);
							/**
Returns the contained value, if the <code>optional</code> contains one. Otherwise, throws an exception.
							*/
							function get() returns (T);
							/**
Returns the contained value, if the optional contains one. Otherwise, returns default.
              @custom:version min=0.71.0              
							*/
              function getOr(T default) returns (T);
							/**
Returns the contained value, if the optional contains one. Otherwise, returns the default value for T type.
              @custom:version min=0.71.0              
							*/
              function getOrDefault() returns (T);
							/**
Replaces content of the <code>optional</code> with <strong>value</strong>.
							*/
							function set(T value);
							/**
Deletes content of the <code>optional</code>.
							*/
							function reset();
                            // reset is the final function here!
      }
    """, "reset", "optional(int) ad; ad.")
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
Returns the last value from the <code>vector</code>.
							*/
							function length() returns (uint8);
							/**
Returns length of the <code>vector</code>.
@custom:version min=0.74.0
							*/
							function last() returns (Type);
							/**
Checks whether the <code>vector</code> is empty.
							*/
							function empty() returns (bool);
                            // empty is the final function here!
      }
    """, "empty", "vector(string) ff; ff.")
  }

  val stackType: SolContract by lazy {
    contract("""
      /**
       @custom:typeArgument Type=T0
       @custom:version min=0.74.0
      */
      contract ${internalise("Stack")} {
							/**
      Pushes an item onto the top of this stack.
      @custom:version min=0.74.0
      */
      function push(Type item);
							/**
      Removes the item at the top of this stack and returns that item as the value of this function.
      @custom:version min=0.74.0
							*/
      function pop() returns (Type);
      
							/**
      Returns reference at the item at the top of this stack without removing it from the stack. Example:
      
      <pre><code>stack(int) st;
      st.push(200);
      st.top() += 25; // st == [225]
      int item = st.top(); // item = 225, st == [225]</code></pre>
        @custom:version min=0.74.0
							*/
      function top() returns (Type ref);
							/**
      Checks whether the stack is empty.
      @custom:version min=0.74.0
							*/
      function empty() returns (bool);
      							/**
      
      Sorts the specified stack into ascending order. Example:
      
      <pre><code>struct Point {
          int x;
          int y;
      }
      
      function less(Point a, Point b) private pure returns(bool) {
          return a.x < b.x || a.x == b.x && a.y < b.y;
      }
      
      function testPoints() public pure {
          stack(Point) st;
          st.push(Point(20, 40));
          st.push(Point(10, 10));
          st.push(Point(20, 30));
          st.sort(less);
          Point p;
          p = st.pop(); // p == Point(10, 10)
          p = st.pop(); // p == Point(20, 30)
          p = st.pop(); // p == Point(20, 40)
      }</code></pre>
      @custom:version min=0.74.0
      */
      function sort(compareCallback) internal pure returns(bool);
      
      /**
      Reverses the order of the elements in the specified stack. Example:
      <pre><code>
      stack(int) st;
      st.push(100);
      st.push(200);
      st.push(300);
      int value = st.top(); // value == 300 
      st.reverse();
      value = st.pop(); // value == 100
      value = st.pop(); // value == 200
      value = st.pop(); // value == 300</code></pre>
      @custom:version min=0.74.0
							*/
      function reverse();
      // reverse is the final function here!
      }
    """, "reverse", "stack(string) ff; ff.")
  }



  val abiType: SolContract by lazy {
    contract("""
      contract ${internalise("Abi")} {
      							/**
                Generates data field of the <code>StateInit</code> (<a href="https://test.ton.org/tblkch.pdf">TBLKCH</a> - 3.1.7.). Parameters are the same as in <a href="https://github.com/everx-labs/TVM-Solidity-Compiler/blob/master/API.md#abiencodestateinit">abi.encodeStateInit()</a>.
                <pre><code>// SimpleWallet.sol
                contract SimpleWallet {
                    uint static m_id;
                    address static m_creator;
                    // ...
                }
                
                // Usage
                TvmCell data = abi.encodeData({
                    contr: SimpleWallet,
                    varInit: {m_id: 1, m_creator: address(this)},
                    pubkey: 0x3f82435f2bd40915c28f56d3c2f07af4108931ae8bf1ca9403dcf77d96250827
                });
                TvmCell code = ...;
                TvmCell stateInit = abi.encodeStateInit({code: code, data: data});
                
                // Note, the code above can be simplified to:
                TvmCell stateInit = abi.encodeStateInit({
                    code: code,
                    contr: SimpleWallet,
                    varInit: {m_id: 1, m_creator: address(this)},
                    pubkey: 0x3f82435f2bd40915c28f56d3c2f07af4108931ae8bf1ca9403dcf77d96250827
                });</code></pre>
                @custom:version min=0.73.0
                @custom:no_validation
							*/
							function encodeData(uint256 pubkey, Contract, Type varInit);
       
       							/**
       Same as <code>abi.encodeData()</code> but generate data in the format that was used in the compiler < 0.72.0. This function can be used to deploy old contracts (that was compiled < 0.72.0) from new ones.
                       @custom:version min=0.73.0
       
							*/
							function encodeOldDataInit() returns (TvmCell);
  						/**
                @custom:version min=0.73.0
							*/
							function decodeData(ContractName name, TvmSlice) returns (uint256 /*pubkey*/, uint64 /*timestamp*/, bool /*constructorFlag*/, Type1 /*var1*/, Type2 /*var2*/);
       				/**
                @custom:version min=0.73.0
							*/
							function encodeStateInit(TvmCell code, TvmCell data) returns (TvmCell stateInit); 
     					/**
                @custom:version min=0.73.0
							*/
							function encodeStateInit(TvmCell code, TvmCell data, uint8 splitDepth) returns (TvmCell stateInit); 
 							/**
                @custom:version min=0.73.0
                @custom:no_validation
							*/
 
							function encodeStateInit(TvmCell code, TvmCell data, uint8 splitDepth, uint256 pubkey, Contract contr, Type varInit); 
       				/**
                @custom:version min=0.73.0
							*/
							function stateInitHash(uint256 codeHash, uint256 dataHash, uint16 codeDepth, uint16 dataDepth) returns (uint256);
 
        				/**
                @custom:version min=0.73.0
                @custom:no_validation
							*/
							function encodeBody(functionOrContract, Any varargs) returns (TvmCell);
        				/**
                @custom:version min=0.73.0
                @custom:no_validation
							*/
							function encodeBody(functionOrContract, callbackFunction, Any varargs) returns (TvmCell);
							 
       				/**
                @custom:version min=0.73.0
                @custom:no_validation
							*/
							function decodeFunctionParams(functionName) returns (TypeA /*a*/, TypeB /*b*/); 
       				/**
                @custom:version min=0.73.0
							*/
							function codeSalt(TvmCell code) returns (optional(TvmCell) optSalt); 
 
        				/**
                @custom:version min=0.73.0
							*/
							function setCodeSalt(TvmCell code, TvmCell salt) returns (TvmCell newCode);
       				/**
                @custom:version min=0.73.0
                @custom:no_validation
							*/
							function functionId(functionOrContractName) returns (uint32);
       				/**
                @custom:version min=0.73.0
                @custom:no_validation
							*/
							function encodeExtMsg(
							    address dest,
							    uint64 time,
							    uint32 expire,
							    functionIdentifier call,
							    bool sign,
							    optional(uint256) pubkey,
							    uint32 callbackId,
							    uint32 onErrorId ,
							    TvmCell stateInit,
							    optional(uint32) signBoxHandle,
							    uint8 abiVer,
							    uint8 flags
							)
							returns (TvmCell);

       				/**
                @custom:version min=0.73.0
                @custom:no_validation
							*/
							function encodeIntMsg(
							    address dest,
							    uint128 value,
							    function call,
							    bool bounce,
							    mapping(uint32 => varUint32) currencies,
							    TvmCell stateInit
							)
							returns (TvmCell);
      
							/**
                creates <code>cell</code> from the values.
                @custom:no_validation
                @custom:typeArgument Type
							*/
							function encode(AnyType varargs) returns (TvmCell /*cell*/);
							/**
                decodes the <code>cell</code> and returns the values.
               @custom:no_validation
               @custom:typeArgument Type:TypeSequence
							*/
							function decode(TvmCell cell, Type varargs) returns (Type);
                    // decode is the final function here!
                    
      }
    """, "decode", "abi.")
  }

  val structType: SolContract by lazy {
    contract("""
      contract ${internalise("Struct")} {
							/**
               @custom:typeArgument Type:DecodedElement
							*/
							function unpack() returns (Type);
      }
    """, "", "")
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
              @custom:version min=0.62.0
							*/
							function toUpperCase() returns (string);
							/**
              @custom:version min=0.62.0
							*/
							function toLowerCase() returns (string);
                            // toLowerCase is the final function here!
        }
    """, "toLowerCase", "\"aa\".")
  }

  val stringBuilderType: SolContract by lazy {
    contract("""
      contract StringBuilder {
							/**
                Appends <code>bytes1</code> to the sequence.
                @custom:version min=0.74.0
							*/
							function append(bytes1);
							/**
                Appends <code>bytes1</code> <code>n</code> times to the sequence.
                @custom:version min=0.74.0
							*/
							function append(bytes1, uint31 n);
							/**
                Appends <code>string</code> to the sequence.
                @custom:version min=0.74.0
							*/
							function append(string);
							/**
                Returns a string representing the data in this sequence.
                @custom:version min=0.74.0
							*/
							function toString();
                            // toString is the final function here!
        }
    """, "toString", "StringBuilder aa; aa.")
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
                            // shuffle is the final function here!
        }
    """, "shuffle", "rnd.")
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

                            // append is the final function here!
      }
    """, "append", "bytes bb; bb.")
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
              @custom:version min=0.63.0
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
							function rawConfigParam(uint8 paramNumber) returns (optional(TvmCell)); 
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
              @custom:version max=0.71.0
							*/
							function rawReserve(uint value, ExtraCurrencyCollection currency, uint8 flag);

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
              @custom:version min=0.72.0
							*/
							function rawReserve(uint value, mapping(uint32 => varUint32) currency, uint8 flag);
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
              @custom:version max=0.72.0
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
              @custom:version max=0.71.0
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
           							Generates an internal outbound message that contains a function call. The result <code>TvmCell</code> can be used to send a message using <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmsendrawmsg">tvm.sendrawmsg()</a> If the <code>function</code> is <code>responsible</code> then <code>callbackFunction</code> parameter must be set.
           
           							<code>dest</code>, <code>value</code> and <code>call</code> parameters are mandatory. Another parameters can be omitted. See <a href=https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#addresstransfer"><address>.transfer()</a> where these options and their default values are described.
           
           							See also:
           							<li>sample <a href="https://github.com/tonlabs/samples/blob/master/solidity/22_sender.sol">22_sender.sol</a></li>
           							<li><a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmencodebody">tvm.encodeBody()</a></li>
           @custom:version min=0.72.0
           							*/
           							function buildIntMsg(
                          address dest,
                          uint128 value,
           							/**
           							*/
           							functionName/*, [callbackFunction,] arg0, arg1, arg2, ...}*/ call,
                          bool bounce,
                          mapping(uint32 => varUint32) currencies, 
                          TvmCell stateInit 
                      )
                      returns (TvmCell);
            /**
            Send the internal/external message <code>msg</code> with <code>flag</code>. It's a wrapper for opcode <code>SENDRAWMSG</code> (<a href="https://test.ton.org/tvm.pdf">TVM</a> - A.11.10). Internal message <code>msg</code> can be generated by <a href="https://github.com/tonlabs/TON-Solidity-Compiler/blob/master/API.md#tvmbuildintmsg">tvm.buildIntMsg()</a> Possible values of <code>flag</code> are described here: <a href=""><address>.transfer()</a>
            
            <strong>Note</strong>: make sure that <code>msg</code> has a correct format and follows the <a href="https://github.com/ton-blockchain/ton/blob/master/crypto/block/block.tlb">TL-B scheme</a> of <code>Message X</code>. For example:\
            <pre><code>TvmCell msg = ...
            tvm.sendrawmsg(msg, 2);</code></pre>

            If the function is called by external message and <code>msg</code> has a wrong format (for example, the field <code>init</code> of <code>Message X</code> is not valid) then the transaction will be replayed despite the usage of flag 2. It will happen because the transaction will fail at the action phase.
            */
            function sendrawmsg(TvmCell msg, uint8 flag);
            
           // sendrawmsg is the final function here!
           
      }
    """, "sendrawmsg", "tvm.")
  }


  val mathType: SolContract by lazy {
    contract("""
      contract ${internalise("Math")} {
							/**
							Returns the minimal (maximal) value of the passed arguments. <code>T</code> should be an integer or fixed point type
                @custom:typeArgument T:Number
							*/
							function min(T varargs) returns (T);
							/**
							Returns the minimal (maximal) value of the passed arguments. <code>T</code> should be an integer or fixed point type
                @custom:typeArgument T:Number
							*/
							function max(T varargs) returns (T);
							/**
							Returns minimal and maximal values of the passed arguments. <code>T</code> should be an integer or fixed point type

							Example:
							<code>(uint a, uint b) = math.minmax(20, 10); // (10, 20)</code>
              @custom:typeArgument T:Number
							*/
							function minmax(T varargs) returns (T /*min*/, T /*max*/);
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
              Same as <code>math.muldivmod()</code> but returns only remainder. Example:
              
              <code>uint constant P = 2**255 - 19;
              
              function f() public pure {
                  uint a = rnd.next(P);
                  uint b = rnd.next(P);
                  uint c = math.mulmod(a, b, P);
                  //...</code>
              }
              @custom:typeArgument T:Int
              @custom:version min=0.75.0
              */
              function mulmod(T a, T b, T c) returns (T /*remainder*/);
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

                           // sign is the final function here!
           
      }
    """, "sign", "math.")
  }

  val blsType: SolType by lazy {
    contract("""
      contract ${internalise("Bls")}{
          /** 
          Checks BLS signature. Returns <code>true</code> on success, <code>false</code> otherwise.
          @custom:version min=0.75.0
          */
          function verify(TvmSlice pubkey, TvmSlice message, TvmSlice sign) returns (bool);

          /** 
          Aggregates signatures if <code>signs.length() > 0</code>. Throws an exception if <code>signs.empty()</code> or if some <code>signs[i]</code> is not a valid signature.

          <pre><code>
          vector(TvmSlice) signs;
          signs.push("8b1eac18b6e7a38f2b2763c9a03c3b6cff4110f18c4d363eec455463bd5c8671fb81204c4732406d72468a1474df6133147a2240f4073a472ef419f23011ee4d6cf02fceb844398e33e2e331635dace3b26464a6851e10f6895923c568582fbd");
          signs.push("94ec60eb8d2b657dead5e1232b8f9cc0162467b08f02e252e97622297787a74b6496607036089837fe5b52244bbbb6d00d3d7cc43812688451229d9e96f704401db053956c588203ba7638e8882746c16e701557f34b0c08bbe097483aec161e");
          signs.push("8cdbeadb3ee574a4f796f10d656885f143f454cc6a2d42cf8cabcd592d577c5108e4258a7b14f0aafe6c86927b3e70030432a2e5aafa97ee1587bbdd8b69af044734defcf3c391515ab26616e15f5825b4b022a7df7b44f65a8792c54762e579");
          TvmSlice sign = bls.aggregate(signs);</code></pre>
          @custom:version min=0.75.0
          */
          function aggregate(vector(TvmSlice) signs) returns (TvmSlice);

          /** 
          Aggregates signatures if <code>signs.length() > 0</code>. Throws an exception if <code>signs.empty()</code> or if some <code>signs[i]</code> is not a valid signature.
          <pre><code>
          TvmSlice sign0 = "8b1eac18b6e7a38f2b2763c9a03c3b6cff4110f18c4d363eec455463bd5c8671fb81204c4732406d72468a1474df6133147a2240f4073a472ef419f23011ee4d6cf02fceb844398e33e2e331635dace3b26464a6851e10f6895923c568582fbd";
          TvmSlice sign1 = "94ec60eb8d2b657dead5e1232b8f9cc0162467b08f02e252e97622297787a74b6496607036089837fe5b52244bbbb6d00d3d7cc43812688451229d9e96f704401db053956c588203ba7638e8882746c16e701557f34b0c08bbe097483aec161e";
          TvmSlice sign2 = "8cdbeadb3ee574a4f796f10d656885f143f454cc6a2d42cf8cabcd592d577c5108e4258a7b14f0aafe6c86927b3e70030432a2e5aafa97ee1587bbdd8b69af044734defcf3c391515ab26616e15f5825b4b022a7df7b44f65a8792c54762e579";
          TvmSlice sign = bls.aggregate(sign0, sign1, sign2);</code></pre>
          @custom:version min=0.75.0
          */
          function aggregate(TvmSlice vararg) returns (TvmSlice);

          /** 
          Checks aggregated BLS signature for <code>pubkeys</code> and <code>message</code>. Returns <code>true</code> on success, <code>false</code> otherwise. Return false if <code>pubkeys.empty()</code>.
          <pre><code>
          vector(TvmSlice) pubkeys;
          pubkeys.push("a44184a47ad3fc0069cf7a95650a28af2ed715beab28651a7ff433e26c0fff714d21cc5657367bc563c6df28fb446d8f");
          pubkeys.push("832c0eca9f8cae87a1c6362838b34723cf63a1f69e366d64f3c61fc237217c4bea601cfbf4d6c18849ed4f9487b4a20c");
          pubkeys.push("9595aa3c5cb3d7c763fa6b52294ebde264bdf49748efbbe7737c35532db8fabc666bb0d186f329c8bdafddfbdcbc3ca6");
          TvmSlice message = TvmSlice(bytes("Hello, BLS fast aggregate and verify!"));
          TvmSlice singature = "8420b1944c64f74dd67dc9f5ab210bab928e2edd4ce7e40c6ec3f5422c99322a5a8f3a8527eb31366c9a74752d1dce340d5a98fbc7a04738c956e74e7ba77b278cbc52afc63460c127998aae5aa1c3c49e8c48c30cc92451a0a275a47f219602";
          bool ok = bls.fastAggregateVerify(pubkeys, message, singature);</code></pre>
          @custom:version min=0.75.0
          */
          function fastAggregateVerify(vector(TvmSlice) pubkeys, TvmSlice message, TvmSlice signature) returns (bool);

          /** 
          Checks aggregated BLS signature for <code>pubkeys</code> and <code>message</code>. Returns <code>true</code> on success, <code>false</code> otherwise. Return false if <code>pubkeys.empty()</code>.
          <pre><code>
          TvmSlice pk0 = "a44184a47ad3fc0069cf7a95650a28af2ed715beab28651a7ff433e26c0fff714d21cc5657367bc563c6df28fb446d8f";
          TvmSlice pk1 = "832c0eca9f8cae87a1c6362838b34723cf63a1f69e366d64f3c61fc237217c4bea601cfbf4d6c18849ed4f9487b4a20c";
          TvmSlice pk2 = "9595aa3c5cb3d7c763fa6b52294ebde264bdf49748efbbe7737c35532db8fabc666bb0d186f329c8bdafddfbdcbc3ca6";
          TvmSlice message = TvmSlice(bytes("Hello, BLS fast aggregate and verify!"));
          TvmSlice singature = "8420b1944c64f74dd67dc9f5ab210bab928e2edd4ce7e40c6ec3f5422c99322a5a8f3a8527eb31366c9a74752d1dce340d5a98fbc7a04738c956e74e7ba77b278cbc52afc63460c127998aae5aa1c3c49e8c48c30cc92451a0a275a47f219602";
          bool ok = bls.fastAggregateVerify(pk0, pk1, pk2, message, singature);</code></pre>
          @custom:version min=0.75.0
          @custom:no_validation
          */
          function fastAggregateVerify(TvmSlice pubKeysVararg, TvmSlice message, TvmSlice signature) returns (bool);

          /** 
          Checks aggregated BLS signature for key-message pairs <code>pubkeysMessages</code>. Returns <code>true</code> on success, <code>false</code> otherwise. Returns <code>false</code> if <code>pubkeysMessages.empty()</code>.
          <pre><code>
          vector(TvmSlice, TvmSlice) pubkeysMessages;
          TvmSlice pubkey0 = "b75f0360095de73c4790f803153ded0f3e6aefa6f0aac8bfd344a44a3de361e3f6f111c0cf0ad0c4a0861492f9f1aeb1";
          TvmSlice message0 = TvmSlice(bytes("Hello, BLS fast aggregate and verify 0!"));
          pubkeysMessages.push(pubkey0, message0);
          TvmSlice pubkey1 = "a31e12bb4ffa75aabbae8ec2367015ba3fc749ac3826539e7d0665c285397d02b48414a23f8b33ecccc750b3afffacf6";
          TvmSlice message1 = TvmSlice(bytes("Hello, BLS fast aggregate and verify 1!"));
          pubkeysMessages.push(pubkey1, message1);
          TvmSlice pubkey2 = "8de5f18ca5938efa896fbc4894c6044cdf89e778bf88584be48d6a6235c504cd45a44a68620f763aea043b6381add1f7";
          TvmSlice message2 = TvmSlice(bytes("Hello, BLS fast aggregate and verify 2!"));
          pubkeysMessages.push(pubkey2, message2);
          TvmSlice singature = "8b8238896dfe3b02dc463c6e645e36fb78add51dc8ce32f40ecf60a418e92762856c3427b672be67278b5c4946b8c5a30fee60e5c38fdb644036a4f29ac9a039ed4e3b64cb7fef303052f33ac4391f95d482a27c8341246516a13cb72e58097b";
          bool ok = bls.aggregateVerify(pubkeysMessages, singature);</code></pre>

          @custom:version min=0.75.0
          */
          function aggregateVerify(vector(TvmSlice, TvmSlice) pubkeysMessages, TvmSlice signature) returns (bool);

          /** 
          Checks aggregated BLS signature for key-message pairs <code>pubkeysMessages</code>. Returns <code>true</code> on success, <code>false</code> otherwise. Returns <code>false</code> if <code>pubkeysMessages.empty()</code>.
          <pre><code>
          TvmSlice pubkey0 = "b75f0360095de73c4790f803153ded0f3e6aefa6f0aac8bfd344a44a3de361e3f6f111c0cf0ad0c4a0861492f9f1aeb1";
          TvmSlice message0 = TvmSlice(bytes("Hello, BLS fast aggregate and verify 0!"));
          TvmSlice pubkey1 = "a31e12bb4ffa75aabbae8ec2367015ba3fc749ac3826539e7d0665c285397d02b48414a23f8b33ecccc750b3afffacf6";
          TvmSlice message1 = TvmSlice(bytes("Hello, BLS fast aggregate and verify 1!"));
          TvmSlice pubkey2 = "8de5f18ca5938efa896fbc4894c6044cdf89e778bf88584be48d6a6235c504cd45a44a68620f763aea043b6381add1f7";
          TvmSlice message2 = TvmSlice(bytes("Hello, BLS fast aggregate and verify 2!"));
          TvmSlice singature = "8b8238896dfe3b02dc463c6e645e36fb78add51dc8ce32f40ecf60a418e92762856c3427b672be67278b5c4946b8c5a30fee60e5c38fdb644036a4f29ac9a039ed4e3b64cb7fef303052f33ac4391f95d482a27c8341246516a13cb72e58097b";
          bool ok = bls.aggregateVerify(pubkey0, message0, pubkey1, message1,  pubkey2, message2, singature);</code></pre>

          @custom:version min=0.75.0
          @custom:no_validation
          */
          function aggregateVerify(TvmSlice pubKeysVararg, TvmSlice messageVararg,  TvmSlice signature) returns (bool);

          /** 
          Returns zero point in G1.
          @custom:version min=0.75.0
          */
          function g1Zero() returns (TvmSlice);

          /** 
          Returns zero point in G2.
          @custom:version min=0.75.0
          */
          function g2Zero() returns (TvmSlice);

          /** 
          Checks that G1 point <code>x</code> is equal to zero.
          @custom:version min=0.75.0
          */
          function g1IsZero(TvmSlice x) returns (bool);

          /** 
          Checks that G2 point <code>x</code> is equal to zero.
          @custom:version min=0.75.0
          */
          function g2IsZero(TvmSlice x) returns (bool);

          /** 
          Adds two G1 points.
          @custom:version min=0.75.0
          */
          function g1Add(TvmSlice a, TvmSlice b) returns (TvmSlice);

          /** 
          Adds two G2 points.
          @custom:version min=0.75.0
          */
          function g2Add(TvmSlice a, TvmSlice b) returns (TvmSlice);

          /** 
          Subtracts two G1 points.
          @custom:version min=0.75.0
          */
          function g1Sub(TvmSlice a, TvmSlice b) returns (TvmSlice);

          /** 
          Subtracts two G2 points.
          @custom:version min=0.75.0
          */
          function g2Sub(TvmSlice a, TvmSlice b) returns (TvmSlice);

          /** 
          Negates a G1 point.
          @custom:version min=0.75.0
          */
          function g1Neg(TvmSlice x) returns (TvmSlice);

          /** 
          Negates a G2 point.
          @custom:version min=0.75.0
          */
          function g2Neg(TvmSlice x) returns (TvmSlice);

          /** 
          Multiplies G1 point <code>x</code> by scalar <code>s</code>.
          @custom:version min=0.75.0
          */
          function g1Mul(TvmSlice x, int s) returns (TvmSlice);

          /** 
          Multiplies G2 point <code>x</code> by scalar <code>s</code>.
          @custom:version min=0.75.0
          */
          function g2Mul(TvmSlice x, int s) returns (TvmSlice);

          /** 
          Checks that slice <code>x</code> represents a valid element of G1.
          @custom:version min=0.75.0
          */
          function g1InGroup(TvmSlice x) returns (bool);

          /** 
          Checks that slice x represents a valid element of G2.
          @custom:version min=0.75.0
          */
          function g2InGroup(TvmSlice x) returns (bool);

          /** 
          Returns the order of G1 and G2 (approx. 2^255).
          @custom:version min=0.75.0
          */
          function r() returns (uint255);

          /** 
          Calculates <code>x_1*s_1+...+x_n*s_n</code> for G1 points <code>x_i</code> and scalars <code>s_i</code>.
          <pre><code>
          TvmSlice a = bls.mapToG1("7abd13983c76661a98659da83066c71bd6581baf20c82c825b007bf8057a258dc53f7a6d44fb6fdecb63d9586e845d92");
          TvmSlice b = bls.mapToG1("7abd13983c76661118659da83066c71bd6581baf20c82c825b007bf8057a258dc53f7a6d44fb6fdecb63d9586e845d92");
          TvmSlice c = bls.mapToG1("7abd13983c76661118659da83066c71bd658100020c82c825b007bf8057a258dc53f7a6d44fb6fdecb63d9586e845d92");
          vector(TvmSlice, int) values;
          values.push(a, 2);
          values.push(b, 5);
          values.push(c, 13537812947843);

          TvmSlice res = bls.g1MultiExp(values);

          TvmSlice aa = bls.g1Mul(a, 2);
          TvmSlice bb = bls.g1Mul(b, 5);
          TvmSlice cc = bls.g1Mul(c, 13537812947843);
          TvmSlice res2 = bls.g1Add(bls.g1Add(aa, bb), cc);

          require(res == res2);</code></pre>

          @custom:version min=0.75.0
          */
          function g1MultiExp(vector(TvmSlice, int) x_s) returns (TvmSlice);

          /** 
          Calculates <code>x_1*s_1+...+x_n*s_n</code> for G2 points <code>x_i</code> and scalars <code>s_i</code>.
          @custom:version min=0.75.0
          */
          function g2MultiExp(vector(TvmSlice, int) x_s) returns (TvmSlice);

          /** 
          Calculates <code>x_1*s_1+...+x_n*s_n</code> for G1 points <code>x_i</code> and scalars <code>s_i</code>.
          <pre><code>
          TvmSlice a = bls.mapToG1("7abd13983c76661a98659da83066c71bd6581baf20c82c825b007bf8057a258dc53f7a6d44fb6fdecb63d9586e845d92");
          TvmSlice b = bls.mapToG1("7abd13983c76661118659da83066c71bd6581baf20c82c825b007bf8057a258dc53f7a6d44fb6fdecb63d9586e845d92");
          TvmSlice c = bls.mapToG1("7abd13983c76661118659da83066c71bd658100020c82c825b007bf8057a258dc53f7a6d44fb6fdecb63d9586e845d92");

          TvmSlice res = bls.g1MultiExp(a, 2, b, 5, c, 13537812947843);

          TvmSlice aa = bls.g1Mul(a, 2);
          TvmSlice bb = bls.g1Mul(b, 5);
          TvmSlice cc = bls.g1Mul(c, 13537812947843);
          TvmSlice res2 = bls.g1Add(bls.g1Add(aa, bb), cc);

          require(res == res2); </code></pre>

          @custom:version min=0.75.0
          @custom:no_validation
          */
          function g1MultiExp(TvmSlice xVararg, int sVararg) returns (TvmSlice);

          /**
          Calculates <code>x_1*s_1+...+x_n*s_n</code> for G2 points <code>x_i</code> and scalars <code>s_i</code>.
          @custom:version min=0.75.0
          @custom:no_validation
          */
          function g2MultiExp(TvmSlice xVararg, int sVararg) returns (TvmSlice);
          
                 
           // g2MultiExp is the final function here!
            }      
        """, "g2MultiExp", "bls.")
  }

  val blockType: SolType by lazy {
    contract("""
      contract ${internalise("Block")}{
          address coinbase;
          uint difficulty;
          uint gasLimit;
          uint number;
          /**
            @custom:version min=0.67.0
          */
          uint32 public timestamp;
          
          /**
            @custom:version max=0.66.0
          */
          uint64 public timestamp;
          
          function blockhash(uint blockNumber) returns (bytes32);
                 
           // blockhash is the final function here!
            }      
        """, "blockhash", "block.")
  }

  val metaType: SolContract by lazy {
    contract("""
      /**
       @custom:typeArgument T=type
      */
      contract ${internalise("MetaType")} {
							/**
                the smallest value representable by type <code>T</code>.
              @custom:version min=0.70.0
							*/
							function min() returns (T);
							/**
                the largest value representable by type <code>T</code>.
              @custom:version min=0.70.0
							*/
							function max() returns (T);
      }
    """, "append", "bytes bb; bb.")
  }


  val globalType: SolContract by lazy {
    contract("""
      contract Global {
          $blockType block;
          $msgType msg;
          $txType tx;
          $abiType abi;
          $mathType math;
          $blsType bls;
          $tvmType tvm;
          $rndType rnd;
          
          uint32 now;

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
              @custom:version min=0.72.0  
							*/
							function require(bool condition, string message);

              /**
                 require function can be used to check the condition and throw an exception if the condition is not met. The function takes condition and optional parameter: error code (unsigned integer).
              @custom:version min=0.68.0
              */
              function require(bool condition, uint16 errorCode);
              /**
                 require function can be used to check the condition and throw an exception if the condition is not met. The function takes condition and optional parameters: error code (unsigned integer) and the object of any type.
              @custom:version min=0.68.0
              */
              function require(bool condition, uint16 errorCode, Type exceptionArgument);
              /**
                 require function can be used to check the condition and throw an exception if the condition is not met. The function takes condition and optional parameter: error code (unsigned integer).
              @custom:version max=0.67.0
              */
              function require(bool condition, uint256 errorCode);
              /**
                 require function can be used to check the condition and throw an exception if the condition is not met. The function takes condition and optional parameters: error code (unsigned integer) and the object of any type.
              @custom:version max=0.67.0
              */
              function require(bool condition, uint256 errorCode, Type exceptionArgument);
              
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
              @custom:version min=0.68.0
							*/
							function revert(uint16 errorCode);
							/**
                revert function can be used to throw exceptions. The function takes an optional error code (unsigned integer) and the object of any type.
              @custom:version min=0.68.0
							*/
							function revert(uint16 errorCode, Type exceptionArgument);
							/**
                revert function can be used to throw exceptions. The function takes an optional error code (unsigned integer).
              @custom:version max=0.67.0
							*/
							function revert(uint256 errorCode);
							/**
                revert function can be used to throw exceptions. The function takes an optional error code (unsigned integer) and the object of any type.
              @custom:version max=0.67.0
							*/
							function revert(uint256 errorCode, Type exceptionArgument);
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
              @custom:version min=0.63.0
              */
              function gasToValue(uint128 gas) returns (uint128 value)
              /**
              Returns worth of <b>gas</b> in workchain <b>wid</b>. Throws an exception if <b>wid</b> is not equal to <code>0</code> or <code>-1</code>. If <code>wid</code> is omitted than used the contract's <code>wid</code>
              */
              function gasToValue(uint128 gas, int8 wid) returns (uint128 value)
              
              /**
              Counts how much <b>gas</b> could be bought on <b>value</b> nanotons in workchain <b>wid</b>. Throws an exception if <b>wid</b> is not equal to <code>0</code> or <code>-1</code>. If <code>wid</code> is omitted than used the contract's <code>wid</code>
              @custom:version min=0.63.0
              */
              function valueToGas(uint128 value) returns (uint128 gas)
              /**
              Counts how much <b>gas</b> could be bought on <b>value</b> nanotons in workchain <b>wid</b>. Throws an exception if <b>wid</b> is not equal to <code>0</code> or <code>-1</code>. If <code>wid</code> is omitted than used the contract's <code>wid</code>
              */
              function valueToGas(uint128 value, int8 wid) returns (uint128 gas)              
          
              /**
              Dumps <code>log</code> string. This function is a wrapper for TVM instructions <code>PRINTSTR</code> (for constant literal strings shorter than 16 symbols) and <code>STRDUMP</code> (for other strings). 
              <code>logtvm</code> is an alias for <code>tvm.log(string)</code>. Example:
              <pre><code>
              tvm.log("Hello, world!");
              logtvm("99_Bottles");
              
              string s = "Some_text";
              tvm.log(s);
              </code></pre>
              <b>Note:</b> For long strings dumps only the first 127 symbols.
              */
              function logtvm(string log);
              
              /**
              */
              function format(string template, AnyType varargs) returns (string);
              /**
              */
              function stoi(string inputStr) returns (optional(int) /*result*/);
              
              /**
              Returns the remaining gas. Supported only if <code>CapGasRemainingInsn</code> capability is set.
              @custom:version min=0.71.0              
              */
              function gasleft() returns (uint64)
              
              // gasleft is the final function here!
      }
    """, "gasleft", "")
  }

  val finalElements = mutableMapOf<String, String>()

  val allDeclarations = SolResolver.lexicalDeclarations(HashSet(), globalType.ref, globalType.ref).toList()

  data class ApiVersion(val min : Version?, val max: Version?) {
    companion object {
      fun parse(data: String): ApiVersion {
        val elements = data.split(";").associate { it.split("=").let { it[0].trim() to it[1].trim() } }
        fun parseProp(prop: KProperty1<ApiVersion, Version?>) = runCatching { elements[prop.name]?.let { Version(it) } }.getOrNull()
        return ApiVersion(min = parseProp(ApiVersion::min), max = parseProp(ApiVersion::max))
      }
    }
    fun compatible(pragmaRange: Range) : Boolean {
      return (min?.let { !SemVer.isGreaterThenRange(it, pragmaRange) } ?: true) && (max?.let { !SemVer.isLessThenRange(it, pragmaRange) } ?: true)
    }
  }

  fun <T: SolNamedElement> getDeclarations(place: PsiElement, solNamedElements: List<T>): Sequence<T> {
    return (findPragmaVersion(place)?.let { pragma ->
      solNamedElements.filter { it.tagComments(VERSION_TAG)?.let { ApiVersion.parse(it).compatible(pragma) } ?: true }
    } ?: solNamedElements).asSequence()
  }


  private fun contract(@Language("T-Sol") contractBody: String, elementName: String, code: String) =
    SolContract(psiFactory.createContract(contractBody), true).also { if (elementName.isNotBlank()) finalElements[code] = elementName }

}
fun findPragmaVersion(place: PsiElement): Range? {
  return place.containingFile.childrenOfType<SolPragmaDirective>().find { it.identifier.text == "ever" }
    ?.let { it.pragmaAll?.text?.takeIf { it.startsWith("-solidity ") }?.removePrefix("-solidity ") }
    ?.let { runCatching { Range(it.trim()) }.getOrNull() }
}
