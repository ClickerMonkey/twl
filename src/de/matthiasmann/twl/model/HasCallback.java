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
package de.matthiasmann.twl.model;

import de.matthiasmann.twl.utils.CallbackSupport;
import de.matthiasmann.twl.*;

/**
 * A class to manage callbacks.
 *
 * @author Matthias Mann
 */
public class HasCallback {

    private Runnable[] callbacks;

    public HasCallback() {
    }
    
    /**
     * Adds a callback to the list.
     * @param callback the callback
     */
    public void addCallback(Runnable callback) {
        callbacks = CallbackSupport.addCallbackToList(callbacks, callback, Runnable.class);
    }

    /**
     * Removes a callback from the list.
     * @param callback the callback that should be removed
     * @return true if the callback was removed
     */
    public void removeCallback(Runnable callback) {
        callbacks = CallbackSupport.removeCallbackFromList(callbacks, callback, Runnable.class);
    }

    /**
     * Calls all callbacks in reverse order. The callback is allowed to
     * remove itself from the list but not others.
     * If a new callback is added while callbacks are executed then the
     * new callback will not be called.
     */
    protected void doCallback() {
        if(callbacks != null) {
            for(Runnable cb : callbacks) {
                cb.run();
            }
        }
    }
}
