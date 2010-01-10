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
package de.matthiasmann.twl.model;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A simple text area model which represents the complete text as a single
 * paragraph without any styles.
 *
 * @author Matthias Mann
 */
public class SimpleTextAreaModel extends HasCallback implements TextAreaModel {

    private static final HTMLTextAreaModel.StyleInfo EMPTY_STYLE = new HTMLTextAreaModel.StyleInfo();

    private Element element;

    public SimpleTextAreaModel() {
    }

    public SimpleTextAreaModel(String text) {
        setText(text);
    }

    public void setText(String text) {
        element = new HTMLTextAreaModel.TextElementImpl(EMPTY_STYLE, text, true, true);
        doCallback();
    }

    public Iterator<Element> iterator() {
        ArrayList<Element> result = new ArrayList<Element>(1);
        if(element != null) {
            result.add(element);
        }
        return result.iterator();
    }

}
