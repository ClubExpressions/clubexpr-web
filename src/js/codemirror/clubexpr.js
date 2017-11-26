// CodeMirror, copyright (c) by Marijn Haverbeke and others
// Distributed under an MIT license: http://codemirror.net/LICENSE

// Copy-pasted from the Common Lisp mode, then tweaked for rainbow parens.

(function(mod) {
  mod(require("codemirror"));
})(function(CodeMirror) {
"use strict";

CodeMirror.defineMode("clubexpr", function (config) {
  var command = /^Somme$|^Diff$|^Produit$|^Quotient$|^Opposé$|^Inverse$|^Carré$|^Puissance$|^Racine$/;
  var numLiteral = /^(?:[+\-]?(?:\d+|\d*\.\d+)(?:[efd][+\-]?\d+)?|[+\-]?\d+(?:\/[+\-]?\d+)?|#b[+\-]?[01]+|#o[+\-]?[0-7]+|#x[+\-]?[\da-f]+)/;
  var symbol = /[^\s'`,@()\[\]";]/;
  var type;

  function readSym(stream) {
    var ch;
    while (ch = stream.next()) {
      if (ch == "\\") stream.next();
      else if (!symbol.test(ch)) { stream.backUp(1); break; }
    }
    return stream.current();
  }

  function base(stream, state) {
    if (stream.eatSpace()) {type = "ws"; return null;}
    if (stream.match(numLiteral)) return "number";
    var ch = stream.next();
    if (ch == "\\") ch = stream.next();

    if (ch == "(") { type = "open"; return "bracket-" + state.parenDepth; }
    else if (ch == ")" || ch == "]") { type = "close"; return "bracket-" + (state.parenDepth + 6)%7; }
    else {
      var name = readSym(stream);
      type = "symbol";
      if (state.lastType == "open") {
        if (command.test(name)) return "keyword";
        else return "error";
      }
      return "variable";
    }
  }

  return {
    startState: function () {
      return {ctx: {prev: null, start: 0, indentTo: 0}, lastType: null, tokenize: base, parenDepth: 1};
    },

    token: function (stream, state) {
      if (stream.sol() && typeof state.ctx.indentTo != "number")
        state.ctx.indentTo = state.ctx.start + 1;

      type = null;
      var style = state.tokenize(stream, state);
      if (type != "ws") {
        if (state.ctx.indentTo == null) {
          if (type == "symbol" && command.test(stream.current()))
            state.ctx.indentTo = state.ctx.start + config.indentUnit;
          else
            state.ctx.indentTo = "next";
        } else if (state.ctx.indentTo == "next") {
          state.ctx.indentTo = stream.column();
        }
        state.lastType = type;
      }
      if (type == "open") {
          state.ctx = {prev: state.ctx, start: stream.column(), indentTo: null};
          state.parenDepth = (state.parenDepth + 1)%7;
      } else if (type == "close") {
          state.ctx = state.ctx.prev || state.ctx;
          // +6 instead of -1 to avoid negative depth
          state.parenDepth = (state.parenDepth + 6)%7;
      }
      return style;
    },

    indent: function (state, _textAfter) {
      var i = state.ctx.indentTo;
      return typeof i == "number" ? i : state.ctx.start + 1;
    },

    closeBrackets: {pairs: "()[]{}\"\""}
  };
});

CodeMirror.defineMIME("text/x-clubexpr", "clubexpr");

});
