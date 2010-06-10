/*
 * Copyright (c) 2008-2010, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.matthiasmann.twl.textarea;

%%

%class Parser
%unicode
%line
%column
%int

%{
    static final int EOF = 0;
    static final int DOT = 1;
    static final int COMMA = 2;
    static final int IDENT = 3;
    static final int STAR = 4;
    static final int STYLE_BEGIN = 5;
    static final int STYLE_END = 6;
    static final int COLON = 7;
    static final int SEMICOLON = 8;
    static final int GT = 9;

    final StringBuilder sb = new StringBuilder();
    private void append() {
        sb.append(zzBuffer, zzStartRead, zzMarkedPos-zzStartRead);
    }

    public void unexpected() throws java.io.IOException {
        throw new java.io.IOException("Unexpected \""+yytext()+"\" at line "+yyline+", column "+yycolumn);
    }

    public void expect(int token) throws java.io.IOException {
        if(next() != token) unexpected();
    }

    public String value() {
        int start = 0;
        int end = sb.length();
        while(start < end && Character.isWhitespace(sb.charAt(start))) start++;
        while(end > start && Character.isWhitespace(sb.charAt(end-1))) end--;
        return sb.substring(start, end);
    }

    private int token = -1;
    public int peek() throws java.io.IOException {
        if(token < 0) token = yylex();
        return token;
    }
    public int next() throws java.io.IOException {
        if(token < 0) return yylex();
        int tmp = token;
        token = -1;
        return tmp;
    }
%}

LineTerminator = \r|\n|\r\n
WhiteSpace = {LineTerminator} | [ \t\f]
Comment = "/*" [^*] ~"*/" | "/*" "*"+ "/"

Identifier = [-]?[_a-z][_a-z0-9-]*

%state YYSTYLE, YYVALUE, YYSTRING1, YYSTRING2

%%

<YYINITIAL> {
    "."                 { return DOT; }
    ","                 { return COMMA; }
    "*"                 { return STAR; }
    ">"                 { return GT; }
    "{"                 { yybegin(YYSTYLE); return STYLE_BEGIN; }

    {Comment}           { /* ignore */ }
    {WhiteSpace}        { /* ignore */ }
    {Identifier}        { return IDENT; }
}

<YYSTYLE> {
    "}"                 { yybegin(YYINITIAL); return STYLE_END; }
    ":"                 { yybegin(YYVALUE); sb.setLength(0); return COLON; }

    {Comment}           { /* ignore */ }
    {WhiteSpace}        { /* ignore */ }
    {Identifier}        { return IDENT; }
}

<YYVALUE> {
    "}"                 { yybegin(YYINITIAL); return STYLE_END; }
    ";"                 { yybegin(YYSTYLE); return SEMICOLON; }
    \'                  { yybegin(YYSTRING1); sb.append('\''); }
    \"                  { yybegin(YYSTRING2); sb.append('\"'); }
    [^;\}\'\"]+         { append(); }
}

<YYSTRING1> {
    \'                  { yybegin(YYVALUE); sb.append('\''); }
    [^\']+              { append(); }
}

<YYSTRING2> {
    \"                  { yybegin(YYVALUE); sb.append('\"'); }
    [^\"]+              { append(); }
}

/* error fallback */
.|\n                    { unexpected(); }
<<EOF>>                 { return EOF; }
