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

import de.matthiasmann.twl.utils.ParameterStringParser;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Style which is constructed from a CSS style string.
 * 
 * @author Matthias Mann
 */
public class CSSStyle extends Style {

    public CSSStyle(String cssStyle) {
        parseCSS(cssStyle);
    }

    public CSSStyle(Style parent, String classRef, String cssStyle) {
        super(parent, classRef);
        parseCSS(cssStyle);
    }

    private void parseCSS(String style) {
        ParameterStringParser psp = new ParameterStringParser(style, ';', ':');
        psp.setTrim(true);
        while(psp.next()) {
            try {
                parseCSSAttribute(psp.getKey(), psp.getValue());
            } catch(IllegalArgumentException ex) {
                Logger.getLogger(CSSStyle.class.getName()).log(Level.SEVERE,
                        "Unable to parse CSS attribute: " + psp.getKey() + "=" + psp.getValue(), ex);
            }
        }
    }

    private void parseCSSAttribute(String key, String value) {
        if("margin-top".equals(key)) {
            parseValueUnit(StyleAttribute.MARGIN_TOP, value);
            return;
        }
        if("margin-left".equals(key)) {
            parseValueUnit(StyleAttribute.MARGIN_LEFT, value);
            return;
        }
        if("margin-right".equals(key)) {
            parseValueUnit(StyleAttribute.MARGIN_RIGHT, value);
            return;
        }
        if("margin-bottom".equals(key)) {
            parseValueUnit(StyleAttribute.MARGIN_BOTTOM, value);
            return;
        }
        if("margin".equals(key)) {
            parseMargin(value);
            return;
        }
        if("text-indent".equals(key)) {
            parseValueUnit(StyleAttribute.TEXT_IDENT, value);
            return;
        }
        if("font".equals(key)) {
            put(StyleAttribute.FONT_NAME, value);
            return;
        }
        if("text-align".equals(key)) {
            parseEnum(StyleAttribute.HORIZONTAL_ALIGNMENT, value);
            return;
        }
        if("vertical-align".equals(key)) {
            parseEnum(StyleAttribute.VERTICAL_ALIGNMENT, value);
            return;
        }
        if("white-space".equals(key)) {
            parseEnum(StyleAttribute.PREFORMATTED, PRE, value);
            return;
        }
        if("list-style-image".equals(key)) {
            parseURL(StyleAttribute.LIST_STYLE_IMAGE, value);
            return;
        }
        if("clear".equals(key)) {
            parseEnum(StyleAttribute.CLEAR, value);
            return;
        }
        if("float".equals(key)) {
            parseEnum(StyleAttribute.FLOAT_POSITION, value);
            return;
        }
        if("display".equals(key)) {
            parseEnum(StyleAttribute.DISPLAY, value);
            return;
        }
        if("width".equals(key)) {
            parseValueUnit(StyleAttribute.WIDTH, value);
            return;
        }
        if("background-image".equals(key)) {
            parseURL(StyleAttribute.BACKGROUND_IMAGE, value);
            return;
        }
        throw new IllegalArgumentException("Unsupported key: " + key);
    }

    private void parseMargin(String value) {
        Value[] vu = parseValueUnits(value);
        switch(vu.length) {
            case 1:
                put(StyleAttribute.MARGIN_TOP, vu[0]);
                put(StyleAttribute.MARGIN_LEFT, vu[0]);
                put(StyleAttribute.MARGIN_RIGHT, vu[0]);
                put(StyleAttribute.MARGIN_BOTTOM, vu[0]);
                break;
            case 2: // TB, LR
                put(StyleAttribute.MARGIN_TOP, vu[0]);
                put(StyleAttribute.MARGIN_LEFT, vu[1]);
                put(StyleAttribute.MARGIN_RIGHT, vu[1]);
                put(StyleAttribute.MARGIN_BOTTOM, vu[0]);
                break;
            case 3: // T, LR, B
                put(StyleAttribute.MARGIN_TOP, vu[0]);
                put(StyleAttribute.MARGIN_LEFT, vu[1]);
                put(StyleAttribute.MARGIN_RIGHT, vu[1]);
                put(StyleAttribute.MARGIN_BOTTOM, vu[2]);
                break;
            case 4: // T, R, B, L
                put(StyleAttribute.MARGIN_TOP, vu[0]);
                put(StyleAttribute.MARGIN_LEFT, vu[3]);
                put(StyleAttribute.MARGIN_RIGHT, vu[1]);
                put(StyleAttribute.MARGIN_BOTTOM, vu[2]);
                break;
            default:
                throw new IllegalArgumentException("Invalid number of margin values: " + vu.length);
        }
    }

    private Value parseValueUnit(String value) {
        Value.Unit unit;
        int suffixLength = 2;
        if(value.endsWith("px")) {
            unit = Value.Unit.PX;
        } else if(value.endsWith("em")) {
            unit = Value.Unit.EM;
        } else if(value.endsWith("ex")) {
            unit = Value.Unit.EX;
        } else if(value.endsWith("%")) {
            suffixLength = 1;
            unit = Value.Unit.PERCENT;
        } else if("0".equals(value)) {
            return Value.ZERO_PX;
        } else {
            throw new IllegalArgumentException("Unknown numeric suffix: " + value);
        }

        String numberPart = value.substring(0, value.length() - suffixLength).trim();
        return new Value(Float.parseFloat(numberPart), unit);
    }

    private Value[] parseValueUnits(String value) {
        String[] parts = value.split("\\s+");
        Value[] result = new Value[parts.length];
        for(int i=0 ; i<parts.length ; i++) {
            result[i] = parseValueUnit(parts[i]);
        }
        return result;
    }

    private void parseValueUnit(StyleAttribute attribute, String value) {
        put(attribute, parseValueUnit(value));
    }

    private<T> void parseEnum(StyleAttribute<T> attribute, HashMap<String, T> map, String value) {
        T obj = map.get(value);
        if(obj == null) {
            throw new IllegalArgumentException("Unknown value: " + value);
        }
        put(attribute, obj);
    }

    private<E extends Enum<E>> void parseEnum(StyleAttribute<E> attribute, String value) {
        E obj = Enum.valueOf(attribute.getDataType(), value.toUpperCase());
        put(attribute, obj);
    }

    private void parseURL(StyleAttribute<String> attribute, String value) {
        if(value.startsWith("url(") && value.endsWith(")")) {
            value = value.substring(4, value.length() - 1).trim();
            if((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
        }
        put(attribute, value);
    }
    
    private static final HashMap<String, Boolean> PRE = new HashMap<String, Boolean>();

    static {
        PRE.put("pre", Boolean.TRUE);
        PRE.put("normal", Boolean.FALSE);
    }
}
