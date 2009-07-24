/*
 * Copyright (c) 2008-2009, Matthias Mann
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

import de.matthiasmann.twl.Label.CallbackReason;
import de.matthiasmann.twl.model.TreeTableNode;
import de.matthiasmann.twl.utils.CallbackSupport;
import org.lwjgl.input.Keyboard;

/**
 * Display widget for a TreeTableNode with navigation and editing support
 *
 * @author Matthias Mann
 */
public class TreePathDisplay extends Widget {

    public interface Callback {
        public void pathElementClicked(TreeTableNode node, TreeTableNode child);
        public void resolvePath(String path);
    }
    
    private final BoxLayout pathBox;
    private final EditField editField;
    private Callback[] callbacks;
    private String separator = "/";
    private TreeTableNode currentNode;

    public TreePathDisplay() {
        pathBox = new BoxLayout(BoxLayout.Direction.HORIZONTAL);
        pathBox.setTheme("pathbox");
        pathBox.setScroll(true);
        pathBox.setClip(true);

        editField = new EditField() {
            @Override
            protected void keyboardFocusLost() {
                setVisible(false);
            }
        };
        editField.setVisible(false);
        editField.addCallback(new EditField.Callback() {
            public void callback(int key) {
                switch(key) {
                    case Keyboard.KEY_RETURN:
                        fireResolvePath(editField.getText());
                        // fall through

                    case Keyboard.KEY_ESCAPE:
                        editField.setVisible(false);
                        requestKeyboardFocus();
                        break;
                }
            }
        });
        
        add(pathBox);
        add(editField);
    }

    public void addCallback(Callback cb) {
        callbacks = CallbackSupport.addCallbackToList(callbacks, cb, Callback.class);
    }

    public void removeCallback(Callback cb) {
        callbacks = CallbackSupport.removeCallbackFromList(callbacks, cb);
    }
    
    public TreeTableNode getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(TreeTableNode currentNode) {
        this.currentNode = currentNode;
        rebuildPathBox();
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
        rebuildPathBox();
    }

    protected String getTextFromNode(TreeTableNode node) {
        Object data = node.getData(0);
        String text = (data != null) ? data.toString() : "";
        if(text.endsWith(separator)) {
            // strip of separator
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private void rebuildPathBox() {
        pathBox.removeAllChildren();
        if(currentNode != null) {
            recursiveAddNode(currentNode, null);
        }
    }

    private void recursiveAddNode(final TreeTableNode node, final TreeTableNode child) {
        if(node.getParent() != null) {
            recursiveAddNode(node.getParent(), node);

            Button btn = new Button(getTextFromNode(node));
            btn.setTheme("node");
            btn.addCallback(new Runnable() {
                public void run() {
                    firePathElementClicked(node, child);
                }
            });
            pathBox.add(btn);

            Label l = new Label(separator);
            l.setTheme("separator");
            l.addCallback(new CallbackWithReason<Label.CallbackReason>() {
                public void callback(CallbackReason reason) {
                    if(reason == CallbackReason.DOUBLE_CLICK) {
                        editPath(node);
                    }
                }
            });
            pathBox.add(l);
        }
    }

    private void editPath(TreeTableNode cursorAfterNode) {
        StringBuilder sb = new StringBuilder();
        int cursorPos = recursiveAddPath(sb, currentNode, cursorAfterNode);
        editField.setText(sb.toString());
        editField.setCursorPos(cursorPos, false);
        editField.setVisible(true);
        editField.requestKeyboardFocus();
    }

    private int recursiveAddPath(StringBuilder sb, TreeTableNode node, TreeTableNode cursorAfterNode) {
        int cursorPos = 0;
        if(node.getParent() != null) {
            cursorPos = recursiveAddPath(sb, node.getParent(), cursorAfterNode);
            sb.append(getTextFromNode(node)).append(separator);
        }
        if(node == cursorAfterNode) {
            return sb.length();
        } else {
            return cursorPos;
        }
    }

    protected void fireResolvePath(String text) {
        if(callbacks != null) {
            for(Callback cb : callbacks) {
                cb.resolvePath(text);
            }
        }
    }

    protected void firePathElementClicked(TreeTableNode node, TreeTableNode child) {
        if(callbacks != null) {
            for(Callback cb : callbacks) {
                cb.pathElementClicked(node, child);
            }
        }
    }

    @Override
    public int getPreferredInnerWidth() {
        return pathBox.getPreferredWidth();
    }

    @Override
    public int getPreferredInnerHeight() {
        return Math.max(
                pathBox.getPreferredHeight(),
                editField.getPreferredHeight());
    }

    @Override
    public int getMinHeight() {
        return Math.max(super.getMinHeight(), Math.max(
                pathBox.getMinHeight(),
                editField.getMinHeight()));
    }

    @Override
    protected void layout() {
        pathBox.setPosition(getInnerX(), getInnerY());
        pathBox.setSize(getInnerWidth(), getInnerHeight());

        editField.setPosition(getInnerX(), getInnerY());
        editField.setSize(getInnerWidth(), getInnerHeight());
    }

}
