/*
 * Copyright (c) 2008, Matthias Mann
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

/**
 * A button which opens a popup menu. Can be inserted in another popup menu.
 *
 * @author Matthias Mann
 */
public class SubMenu extends Button {

    private final PopupMenu popupMenu;

    public SubMenu() {
        this.popupMenu = new PopupMenu(this);
        
        addCallback(new Runnable() {
            public void run() {
                buttonAction();
            }
        });
    }

    public SubMenu(String text) {
        this();
        setText(text);
    }

    public PopupMenu getPopupMenu() {
        return popupMenu;
    }

    @Override
    public void insertChild(Widget child, int index) throws IndexOutOfBoundsException {
        if(child instanceof SubMenu) {
            throw new IllegalArgumentException("SubMenu must be added to the contained PopupMenu");
        }
        super.insertChild(child, index);
    }

    protected void buttonAction() {
        popupMenu.showPopup(getRight(), getY());
    }

    protected PopupMenu getParentPopupMenu() {
        Widget parent = getParent();
        if(!(parent instanceof PopupMenu)) {
            return null;
        }
        return (PopupMenu)parent;
    }
}
