// author: jonathan bachrach
package Chisel {

import scala.math.max;
import Node._;

object Op {
  def apply (name: String, nGrow: Int, widthInfer: (Node) => Int, a: Node, b: Node): Node = {
    val (a_lit, b_lit) = (a.litOf, b.litOf);
    // println("OP " + name + " " + a + " " + a_lit + " " + b + " " + b_lit);
    if (a_lit != null && b_lit == null) {
      name match {
        case "&&" => return if (a_lit.value == 0) Literal(0) else b;
        case "||" => return if (a_lit.value == 0) b else Literal(1);
        case _ => ;
      }
    } else if (a_lit == null && b_lit != null) {
      name match {
        case "&&" => return if (b_lit.value == 0) Literal(0) else a;
        case "||" => return if (b_lit.value == 0) a else Literal(1);
        case _ => ;
      }
    } else if (a_lit != null && b_lit != null) {
      val (aw, bw) = (a_lit.width, b_lit.width);
      val (av, bv) = (a_lit.value, b_lit.value);
      name match {
        case "##" => return Literal(av << bw | bv, aw + bw);
        case "+"  => return Literal(av + bv, max(aw, bw)+1);
        case "-"  => return Literal(av - bv, max(aw, bw)+1);
        case "|"  => return Literal(av | bv, max(aw, bw));
        case "&"  => return Literal(av & bv, max(aw, bw));
        case "^"  => return Literal(av ^ bv, max(aw, bw));
        case "<<" => return Literal(av << bv.toInt, aw + bv.toInt);
        case ">>" => return Literal(av >> bv.toInt, aw - bv.toInt);
        case _ => ;
      } 
    }
    val res = new Op();
    res.init("", widthInfer, a, b);
    res.op = name;
    res.nGrow = nGrow;
    res.isSigned = a.isInstanceOf[Fix] && b.isInstanceOf[Fix]
    // println(a + " " + a.litOf + " " + name + " " + b + " " + b.litOf);
    // if(res.isSigned) println("SIGNED OPERATION DETECTED " + name);
    res
  }
  def apply (name: String, nGrow: Int, widthInfer: (Node) => Int, a: Node): Node = {
    val res = new Op();
    res.init("", widthInfer, a);
    res.op = name;
    res.nGrow = nGrow;
    res
  }
}
class Op extends Node {
  var op: String = "";
  var nGrow: Int = 0;
  override def toString: String =
    if (inputs.length == 1)
      op + inputs(0)
    else
      inputs(0) + op + inputs(1)

  def emitOpRef (k: Int): String = {
    if (op == "<<") {
      if (k == 0 && inputs(k).width < width)
	"/* C */DAT<" + width + ">(" + inputs(k).emitRef + ")"
      else
	inputs(k).emitRef
    } else if (op == "##" || op == ">>" || op == "*" ||
             op == "s*s" || op == "u*s" || op == "s*u") {
      inputs(k).emitRef
    } else {
      var w = 0;
      for (i <- 0 until nGrow)
	w = max(w, inputs(i).width);
      if (isCoercingArgs && nGrow > 0 && k < nGrow && w > inputs(k).width)
	"/* C */DAT<" + w + ">(" + inputs(k).emitRef + ")"
      else
	inputs(k).emitRef
    }
  }

  override def emitDef: String = {
    val c = component;
    "  assign " + emitTmp + " = " + 
      (if (op == "##") 
        "{" + inputs(0).emitRef + ", " + inputs(1).emitRef + "}"
       else if (inputs.length == 1)
         op + " " + inputs(0).emitRef
       else if (op == "s*s")
         "$signed(" + inputs(0).emitRef + ") * $signed(" + inputs(1).emitRef + ")"
       else if (op == "s*u")
         "$signed(" + inputs(0).emitRef + ") * " + inputs(1).emitRef
       else if (op == "u*s")
         inputs(0).emitRef + " * $signed(" + inputs(1).emitRef + ")"
       else if(isSigned)
	 emitDefSigned
       else
         inputs(0).emitRef + " " + op + " " + inputs(1).emitRef
      ) + ";\n"
  }

  def emitDefSigned: String = {
    if (op == ">>")
      "$signed(" + inputs(0).emitRef +") " + op + " " + inputs(1).emitRef
    else
      "$signed(" + inputs(0).emitRef +") " + op + " $signed(" + inputs(1).emitRef + ")"
  }

  override def emitDefLoC: String = {
    "  " + emitTmp + " = " +
      (if (op == "##") 
        "cat<" + width + ">(" + emitOpRef(0) + ", " + emitOpRef(1) + ")"
       else if (op == "s*s")
         inputs(0).emitRef + ".fix_times_fix(" + inputs(1).emitRef + ")"
       else if (op == "s*u")
         inputs(0).emitRef + ".fix_times_ufix(" + inputs(1).emitRef + ")"
       else if (op == "u*s")
         inputs(0).emitRef + ".ufix_times_fix(" + inputs(1).emitRef + ")"
       else if (inputs.length == 1)
         if (op == "|")
           "reduction_or(" + inputs(0).emitRef + ")"
         else if (op == "&")
           "reduction_and(" + inputs(0).emitRef + ")"
         else if (op == "^")
           "reduction_xor(" + inputs(0).emitRef + ")"
         else
           op + inputs(0).emitRef
       else if(isSigned)
	 emitSignedDefLoC;
       else
         emitOpRef(0) + " " + op + " " + emitOpRef(1)) +
    ";\n"
  }

  def emitSignedDefLoC: String = {
    if(op == ">")
      emitOpRef(0) + ".gt(" + emitOpRef(1) + ")"
    else if(op == ">=")
      emitOpRef(0) + ".gte(" + emitOpRef(1) + ")"
    else if(op == "<")
      emitOpRef(0) + ".lt(" + emitOpRef(1) + ")"
    else if(op == "<=")
      emitOpRef(0) + ".lt(" + emitOpRef(1) + ")"
    else 
      emitOpRef(0) + " " + op + " " + emitOpRef(1)
  }
}

}
