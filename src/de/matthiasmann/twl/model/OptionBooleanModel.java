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

/**
 * A BooleanModel which is true when the underlying IntegerModel has the specified
 * option code. This can be used for radio/option buttons.
 *
 * It is not possible to set this BooleanModel to false. It can only be set to
 * false by setting the underlying IntegerModel to another value. Eg by setting
 * another OptionBooleanModel working on the same IntegerModel to true.
 *
 * @author Matthias Mann
 */
public class OptionBooleanModel extends HasCallback implements BooleanModel {

    private final IntegerModel optionState;
    private final int optionCode;

    private boolean value;

    /**
     * Creates a new OptionBooleanModel with the specified IntegerModel and
     * option code.
     *
     * @param opentionState the IntegerModel which stores the current active option
     * @param optionCode the option code of this option in the IntegerModel
     */
    public OptionBooleanModel(IntegerModel opentionState, int optionCode) {
        if(opentionState == null) {
            throw new NullPointerException("opentionState");
        }
        if(optionCode < opentionState.getMinValue() ||
                optionCode > opentionState.getMaxValue()) {
            throw new IllegalArgumentException("optionCode");
        }
        this.optionState = opentionState;
        this.optionCode = optionCode;
        this.value = opentionState.getValue() == optionCode;
        opentionState.addCallback(new Runnable() {
            public void run() {
                optionStateChanged();
            }
        });
    }

    public boolean getValue() {
        return value;
    }

    /**
     * If value is true, then the underlying IntegerModel is set to the
     * option code of this OptionBooleanModel.
     *
     * if value if false then nothing happens.
     *
     * @param value the new value of this BooleanModel
     */
    public void setValue(boolean value) {
        if(value) {
            optionState.setValue(optionCode);
        }
    }

    protected void optionStateChanged() {
        boolean active = optionState.getValue() == optionCode;
        if(value != active) {
            value = active;
            doCallback();
        }
    }
}
