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
package de.matthiasmann.twl;

import org.lwjgl.input.Keyboard;

/**
 * A generic popup window.
 * Must not be added as a child to another Widget.
 *
 * @author Matthias Mann
 */
public class PopupWindow extends Widget {

    private final Widget owner;

    private boolean closeOnClickedOutside = true;
    private boolean closeOnEscape = true;
    
    public PopupWindow(Widget owner) {
        if(owner == null) {
            throw new NullPointerException("owner");
        }
        this.owner = owner;
    }

    public Widget getOwner() {
        return owner;
    }

    public boolean isCloseOnClickedOutside() {
        return closeOnClickedOutside;
    }

    public void setCloseOnClickedOutside(boolean closeOnClickedOutside) {
        this.closeOnClickedOutside = closeOnClickedOutside;
    }

    public boolean isCloseOnEscape() {
        return closeOnEscape;
    }

    public void setCloseOnEscape(boolean closeOnEscape) {
        this.closeOnEscape = closeOnEscape;
    }

    public boolean openPopup() {
        GUI gui = owner.getGUI();
        if(gui != null) {
            // a popup can't be invisible or disabled when it should open
            setVisible(true);
            setEnabled(true);
            // owner's hasOpenPopups flag is handled by GUI
            gui.openPopup(this);
            requestKeyboardFocus();
            focusFirstChild();
            return true;
        }
        return false;
    }
    
    public void openPopupCentered() {
        if(openPopup()) {
            adjustSize();
            setPosition(
                    getParent().getInnerX() + (getParent().getInnerWidth() - getWidth())/2,
                    getParent().getInnerY() + (getParent().getInnerHeight() - getHeight())/2);
        }
    }
    
    public void closePopup() {
        GUI gui = getGUI();
        if(gui != null) {
            // owner's hasOpenPopups flag is handled by GUI
            gui.closePopup(this);
            owner.requestKeyboardFocus();
        }
    }
    
    public boolean isOpen() {
        return getParent() != null;
    }

    @Override
    public int getPreferredInnerWidth() {
        return BoxLayout.computePreferredWidthVertical(this);
    }

    @Override
    public int getPreferredInnerHeight() {
        return BoxLayout.computePreferredHeightHorizontal(this);
    }

    @Override
    protected void layout() {
        layoutChildrenFullInnerArea();
    }
    
    @Override
    protected final boolean handleEvent(Event evt) {
        if(super.handleEvent(evt)) {
            return true;
        }
        if(evt.getType() == Event.Type.MOUSE_CLICKED &&
                !isInside(evt.getMouseX(), evt.getMouseY())) {
            mouseClickedOutside(evt);
            return true;
        }
        if(closeOnEscape &&
                evt.getType() == Event.Type.KEY_PRESSED &&
                evt.getKeyCode() == Keyboard.KEY_ESCAPE) {
            requestPopupClose();
            return true;
        }
        // eat all events
        return true;
    }

    @Override
    protected boolean isMouseInside(Event evt) {
        return true;    // :P
    }

    protected void requestPopupClose() {
        closePopup();
    }

    protected void mouseClickedOutside(Event evt) {
        if(closeOnClickedOutside) {
            requestPopupClose();
        }
    }
}
