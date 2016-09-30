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
package de.matthiasmann.twl.input.lwjgl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.glfw.*;

import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.input.Input;

/**
 * Input handling based on LWJGL's Mouse & Keyboard classes.
 *
 * @author Matthias Mann
 */
public class LWJGLInput implements Input {

    private boolean wasActive;
    
    private static final char[][] KEYCODE_TO_CHAR = new char[ GLFW_KEY_LAST + 1 ][2];
    private static final int[] KEYCODE_TO_KEY = new int[ GLFW_KEY_LAST + 1 ];
    
    private class GLFWInput {
        private GLFWCursorPosCallbackI previousCursorPos;
        private GLFWMouseButtonCallbackI previousMouseButton;
        private GLFWScrollCallbackI previousScroll;
        private GLFWKeyCallbackI previousKey;
        private final ConcurrentLinkedQueue<GLFWKey> keys = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<GLFWMouseButton> mouse = new ConcurrentLinkedQueue<>();
        private double mx, my;
        private double wheel;
    }
    
    private class GLFWKey {
        private final int code;
        private final char character;
        private final boolean pressed;
        private GLFWKey(int code, char character, boolean pressed) {
            this.code = code;
            this.character = character;
            this.pressed = pressed;
        }
    }
    
    private class GLFWMouseButton {
        private final int button;
        private final boolean pressed;
        private final double x;
        private final double y;
        private GLFWMouseButton(int button, boolean pressed, double x, double y) {
            this.button = button;
            this.pressed = pressed;
            this.x = x;
            this.y = y;
        }
    }
    
    private Map<Long, GLFWInput> inputs = new HashMap<>();

    public boolean pollInput(GUI gui) {
        long context = glfwGetCurrentContext();
        
        if (context == 0 && wasActive) {
            return wasActive = false;
        }
        
        if (context == 0) {
            return true;
        }
        
        boolean active = glfwGetWindowAttrib( context, GLFW_FOCUSED ) == GL_TRUE;
        
        if (!active && wasActive) {
            return wasActive = false;
        }
        
        GLFWInput input = inputs.get(context);
        
        if (input == null) {
            input = listenToInput( context );
        }
        
        for (GLFWKey key = input.keys.poll(); key != null; key = input.keys.poll()) {
            gui.handleKey(key.code, key.character, key.pressed);
        }
        
        for (GLFWMouseButton mouse = input.mouse.poll(); mouse != null; mouse = input.mouse.poll()) {
            gui.handleMouse((int)mouse.x, /*gui.getHeight() - */(int)mouse.y - 1, mouse.button, mouse.pressed);
        }
        
        if (input.wheel != 0) {
            gui.handleMouseWheel((int)input.wheel);
        }
        
        input.wheel = 0;
        
        return true;
    }
    
    public GLFWInput listenToInput(long context) {
        GLFWInput input = new GLFWInput();
        
        input.previousCursorPos = GLFW.glfwSetCursorPosCallback(context, callbackCursorPos);
        input.previousKey = glfwSetKeyCallback(context, callbackKey);
        input.previousMouseButton = glfwSetMouseButtonCallback(context, callbackMouseButton);
        input.previousScroll = glfwSetScrollCallback(context, callbackScroll);
        
        inputs.put(context, input);
        
        return input;
    }
    
    private char getKeyChar(int keycode, int shift) {
        return keycode >= 0 && KEYCODE_TO_CHAR[ keycode ] != null ? KEYCODE_TO_CHAR[ keycode ][ shift ] : 0;
    }
    
    private int getKey(int keycode) {
        return keycode >= 0 ? KEYCODE_TO_KEY[keycode] : 0;
    }
    
    private GLFWKeyCallbackI callbackKey = (window, key, scancode, action, mods) -> {
        GLFWInput input = inputs.get(window);
        if (input != null) {
            if (input.previousKey != null) {
                input.previousKey.invoke(window, key, scancode, action, mods);
            }
            if (key != GLFW_KEY_UNKNOWN) {
                input.keys.offer(new GLFWKey(getKey(key), getKeyChar(key, mods & GLFW_MOD_SHIFT), action != GLFW_RELEASE));
            }
        }
    };
    
    private GLFWMouseButtonCallbackI callbackMouseButton = (window, button, action, mods) -> {
        GLFWInput input = inputs.get(window);
        if (input != null) {
            if (input.previousMouseButton != null) {
                input.previousMouseButton.invoke(window, button, action, mods);
            }
            input.mouse.offer(new GLFWMouseButton(button, action != GLFW_RELEASE, input.mx, input.my));
        }
    };
    
    private GLFWCursorPosCallbackI callbackCursorPos = (window, xpos, ypos) -> {
        GLFWInput input = inputs.get(window);
        if (input != null) {
            if (input.previousCursorPos != null) {
                input.previousCursorPos.invoke(window, xpos, ypos);
            }
            input.mx = xpos;
            input.my = ypos;
        }
    };
    
    private GLFWScrollCallbackI callbackScroll = (window, dx, dy) -> {
        GLFWInput input = inputs.get(window);
        if (input != null) {
            if (input.previousScroll != null) {
                input.previousScroll.invoke(window, dx, dy);
            }
            input.wheel += dy;
        }
    };
    
    static {
        KEYCODE_TO_CHAR[ GLFW_KEY_SPACE ] = new char[] {' ', ' '};
        KEYCODE_TO_CHAR[ GLFW_KEY_APOSTROPHE ] = new char[] {'\'', '"'};
        KEYCODE_TO_CHAR[ GLFW_KEY_COMMA ] = new char[] {',', '<'};
        KEYCODE_TO_CHAR[ GLFW_KEY_MINUS ] = new char[] {'-', '_'};
        KEYCODE_TO_CHAR[ GLFW_KEY_PERIOD ] = new char[] {'.', '>'};
        KEYCODE_TO_CHAR[ GLFW_KEY_SLASH ] = new char[] {'/', '?'};
        KEYCODE_TO_CHAR[ GLFW_KEY_0 ] = new char[] {'0', ')'};
        KEYCODE_TO_CHAR[ GLFW_KEY_1 ] = new char[] {'1', '!'};
        KEYCODE_TO_CHAR[ GLFW_KEY_2 ] = new char[] {'2', '@'};
        KEYCODE_TO_CHAR[ GLFW_KEY_3 ] = new char[] {'3', '#'};
        KEYCODE_TO_CHAR[ GLFW_KEY_4 ] = new char[] {'4', '$'};
        KEYCODE_TO_CHAR[ GLFW_KEY_5 ] = new char[] {'5', '%'};
        KEYCODE_TO_CHAR[ GLFW_KEY_6 ] = new char[] {'6', '^'};
        KEYCODE_TO_CHAR[ GLFW_KEY_7 ] = new char[] {'7', '&'};
        KEYCODE_TO_CHAR[ GLFW_KEY_8 ] = new char[] {'8', '*'};
        KEYCODE_TO_CHAR[ GLFW_KEY_9 ] = new char[] {'9', '('};
        KEYCODE_TO_CHAR[ GLFW_KEY_SEMICOLON ] = new char[] {';', ':'};
        KEYCODE_TO_CHAR[ GLFW_KEY_EQUAL ] = new char[] {'=', '+'};
        KEYCODE_TO_CHAR[ GLFW_KEY_A ] = new char[] {'a', 'A'};
        KEYCODE_TO_CHAR[ GLFW_KEY_B ] = new char[] {'b', 'B'};
        KEYCODE_TO_CHAR[ GLFW_KEY_C ] = new char[] {'c', 'C'};
        KEYCODE_TO_CHAR[ GLFW_KEY_D ] = new char[] {'d', 'D'};
        KEYCODE_TO_CHAR[ GLFW_KEY_E ] = new char[] {'e', 'E'};
        KEYCODE_TO_CHAR[ GLFW_KEY_F ] = new char[] {'f', 'F'};
        KEYCODE_TO_CHAR[ GLFW_KEY_G ] = new char[] {'g', 'G'};
        KEYCODE_TO_CHAR[ GLFW_KEY_H ] = new char[] {'h', 'H'};
        KEYCODE_TO_CHAR[ GLFW_KEY_I ] = new char[] {'i', 'I'};
        KEYCODE_TO_CHAR[ GLFW_KEY_J ] = new char[] {'j', 'J'};
        KEYCODE_TO_CHAR[ GLFW_KEY_K ] = new char[] {'k', 'K'};
        KEYCODE_TO_CHAR[ GLFW_KEY_L ] = new char[] {'l', 'L'};
        KEYCODE_TO_CHAR[ GLFW_KEY_M ] = new char[] {'m', 'M'};
        KEYCODE_TO_CHAR[ GLFW_KEY_N ] = new char[] {'n', 'N'};
        KEYCODE_TO_CHAR[ GLFW_KEY_O ] = new char[] {'o', 'O'};
        KEYCODE_TO_CHAR[ GLFW_KEY_P ] = new char[] {'p', 'P'};
        KEYCODE_TO_CHAR[ GLFW_KEY_Q ] = new char[] {'q', 'Q'};
        KEYCODE_TO_CHAR[ GLFW_KEY_R ] = new char[] {'r', 'R'};
        KEYCODE_TO_CHAR[ GLFW_KEY_S ] = new char[] {'s', 'S'};
        KEYCODE_TO_CHAR[ GLFW_KEY_T ] = new char[] {'t', 'T'};
        KEYCODE_TO_CHAR[ GLFW_KEY_U ] = new char[] {'u', 'U'};
        KEYCODE_TO_CHAR[ GLFW_KEY_V ] = new char[] {'v', 'V'};
        KEYCODE_TO_CHAR[ GLFW_KEY_W ] = new char[] {'w', 'W'};
        KEYCODE_TO_CHAR[ GLFW_KEY_X ] = new char[] {'x', 'X'};
        KEYCODE_TO_CHAR[ GLFW_KEY_Y ] = new char[] {'y', 'Y'};
        KEYCODE_TO_CHAR[ GLFW_KEY_Z ] = new char[] {'z', 'Z'};
        KEYCODE_TO_CHAR[ GLFW_KEY_LEFT_BRACKET ] = new char[] {'[', '{'};
        KEYCODE_TO_CHAR[ GLFW_KEY_BACKSLASH ] = new char[] {'\\', '|'};
        KEYCODE_TO_CHAR[ GLFW_KEY_RIGHT_BRACKET ] = new char[] {']', '}'};
        KEYCODE_TO_CHAR[ GLFW_KEY_GRAVE_ACCENT ] = new char[] {'`', '~'};
        KEYCODE_TO_CHAR[ GLFW_KEY_ENTER ] = new char[] {'\n', '\n'};
        KEYCODE_TO_CHAR[ GLFW_KEY_TAB ] = new char[] {'\t', '\t'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_0 ] = new char[] {'0', '0'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_1 ] = new char[] {'1', '1'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_2 ] = new char[] {'2', '2'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_3 ] = new char[] {'3', '3'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_4 ] = new char[] {'4', '4'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_5 ] = new char[] {'5', '5'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_6 ] = new char[] {'6', '6'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_7 ] = new char[] {'7', '7'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_8 ] = new char[] {'8', '8'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_9 ] = new char[] {'9', '9'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_DECIMAL ] = new char[] {'.', '.'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_DIVIDE ] = new char[] {'/', '/'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_MULTIPLY ] = new char[] {'*', '*'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_SUBTRACT ] = new char[] {'-', '-'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_ADD ] = new char[] {'+', '+'};
        KEYCODE_TO_CHAR[ GLFW_KEY_KP_EQUAL ] = new char[] {'=', '='};

        KEYCODE_TO_KEY[ GLFW_KEY_SPACE ] = Event.KEY_SPACE;
        KEYCODE_TO_KEY[ GLFW_KEY_APOSTROPHE ] = Event.KEY_APOSTROPHE;
        KEYCODE_TO_KEY[ GLFW_KEY_COMMA ] = Event.KEY_COMMA;
        KEYCODE_TO_KEY[ GLFW_KEY_MINUS ] = Event.KEY_MINUS;
        KEYCODE_TO_KEY[ GLFW_KEY_PERIOD ] = Event.KEY_PERIOD;
        KEYCODE_TO_KEY[ GLFW_KEY_SLASH ] = Event.KEY_SLASH;
        KEYCODE_TO_KEY[ GLFW_KEY_0 ] = Event.KEY_0;
        KEYCODE_TO_KEY[ GLFW_KEY_1 ] = Event.KEY_1;
        KEYCODE_TO_KEY[ GLFW_KEY_2 ] = Event.KEY_2;
        KEYCODE_TO_KEY[ GLFW_KEY_3 ] = Event.KEY_3;
        KEYCODE_TO_KEY[ GLFW_KEY_4 ] = Event.KEY_4;
        KEYCODE_TO_KEY[ GLFW_KEY_5 ] = Event.KEY_5;
        KEYCODE_TO_KEY[ GLFW_KEY_6 ] = Event.KEY_6;
        KEYCODE_TO_KEY[ GLFW_KEY_7 ] = Event.KEY_7;
        KEYCODE_TO_KEY[ GLFW_KEY_8 ] = Event.KEY_8;
        KEYCODE_TO_KEY[ GLFW_KEY_9 ] = Event.KEY_9;
        KEYCODE_TO_KEY[ GLFW_KEY_SEMICOLON ] = Event.KEY_SEMICOLON;
        KEYCODE_TO_KEY[ GLFW_KEY_EQUAL ] = Event.KEY_EQUALS;
        KEYCODE_TO_KEY[ GLFW_KEY_A ] = Event.KEY_A;
        KEYCODE_TO_KEY[ GLFW_KEY_B ] = Event.KEY_B;
        KEYCODE_TO_KEY[ GLFW_KEY_C ] = Event.KEY_C;
        KEYCODE_TO_KEY[ GLFW_KEY_D ] = Event.KEY_D;
        KEYCODE_TO_KEY[ GLFW_KEY_E ] = Event.KEY_E;
        KEYCODE_TO_KEY[ GLFW_KEY_F ] = Event.KEY_F;
        KEYCODE_TO_KEY[ GLFW_KEY_G ] = Event.KEY_G;
        KEYCODE_TO_KEY[ GLFW_KEY_H ] = Event.KEY_H;
        KEYCODE_TO_KEY[ GLFW_KEY_I ] = Event.KEY_I;
        KEYCODE_TO_KEY[ GLFW_KEY_J ] = Event.KEY_J;
        KEYCODE_TO_KEY[ GLFW_KEY_K ] = Event.KEY_K;
        KEYCODE_TO_KEY[ GLFW_KEY_L ] = Event.KEY_L;
        KEYCODE_TO_KEY[ GLFW_KEY_M ] = Event.KEY_M;
        KEYCODE_TO_KEY[ GLFW_KEY_N ] = Event.KEY_N;
        KEYCODE_TO_KEY[ GLFW_KEY_O ] = Event.KEY_O;
        KEYCODE_TO_KEY[ GLFW_KEY_P ] = Event.KEY_P;
        KEYCODE_TO_KEY[ GLFW_KEY_Q ] = Event.KEY_Q;
        KEYCODE_TO_KEY[ GLFW_KEY_R ] = Event.KEY_R;
        KEYCODE_TO_KEY[ GLFW_KEY_S ] = Event.KEY_S;
        KEYCODE_TO_KEY[ GLFW_KEY_T ] = Event.KEY_T;
        KEYCODE_TO_KEY[ GLFW_KEY_U ] = Event.KEY_U;
        KEYCODE_TO_KEY[ GLFW_KEY_V ] = Event.KEY_V;
        KEYCODE_TO_KEY[ GLFW_KEY_W ] = Event.KEY_W;
        KEYCODE_TO_KEY[ GLFW_KEY_X ] = Event.KEY_X;
        KEYCODE_TO_KEY[ GLFW_KEY_Y ] = Event.KEY_Y;
        KEYCODE_TO_KEY[ GLFW_KEY_Z ] = Event.KEY_Z;
        KEYCODE_TO_KEY[ GLFW_KEY_LEFT_BRACKET ] = Event.KEY_LBRACKET;
        KEYCODE_TO_KEY[ GLFW_KEY_BACKSLASH ] = Event.KEY_BACKSLASH;
        KEYCODE_TO_KEY[ GLFW_KEY_RIGHT_BRACKET ] = Event.KEY_RBRACKET;
        KEYCODE_TO_KEY[ GLFW_KEY_GRAVE_ACCENT ] = Event.KEY_GRAVE;
        KEYCODE_TO_KEY[ GLFW_KEY_ESCAPE ] = Event.KEY_ESCAPE;
        KEYCODE_TO_KEY[ GLFW_KEY_ENTER ] = Event.KEY_RETURN;
        KEYCODE_TO_KEY[ GLFW_KEY_TAB ] = Event.KEY_TAB;
        KEYCODE_TO_KEY[ GLFW_KEY_BACKSPACE ] = Event.KEY_BACK;
        KEYCODE_TO_KEY[ GLFW_KEY_INSERT ] = Event.KEY_INSERT;
        KEYCODE_TO_KEY[ GLFW_KEY_DELETE ] = Event.KEY_DELETE;
        KEYCODE_TO_KEY[ GLFW_KEY_RIGHT ] = Event.KEY_RIGHT;
        KEYCODE_TO_KEY[ GLFW_KEY_LEFT ] = Event.KEY_LEFT;
        KEYCODE_TO_KEY[ GLFW_KEY_DOWN ] = Event.KEY_DOWN;
        KEYCODE_TO_KEY[ GLFW_KEY_UP ] = Event.KEY_UP;
        KEYCODE_TO_KEY[ GLFW_KEY_PAGE_UP ] = Event.KEY_PRIOR;
        KEYCODE_TO_KEY[ GLFW_KEY_PAGE_DOWN ] = Event.KEY_NEXT;
        KEYCODE_TO_KEY[ GLFW_KEY_HOME ] = Event.KEY_HOME;
        KEYCODE_TO_KEY[ GLFW_KEY_END ] = Event.KEY_END;
        KEYCODE_TO_KEY[ GLFW_KEY_CAPS_LOCK ] = Event.KEY_CAPITAL;
        KEYCODE_TO_KEY[ GLFW_KEY_SCROLL_LOCK ] = Event.KEY_SCROLL;
        KEYCODE_TO_KEY[ GLFW_KEY_NUM_LOCK ] = Event.KEY_NUMLOCK;
        KEYCODE_TO_KEY[ GLFW_KEY_PRINT_SCREEN ] = 0;
        KEYCODE_TO_KEY[ GLFW_KEY_PAUSE ] = Event.KEY_PAUSE;
        KEYCODE_TO_KEY[ GLFW_KEY_F1 ] = Event.KEY_F1;
        KEYCODE_TO_KEY[ GLFW_KEY_F2 ] = Event.KEY_F2;
        KEYCODE_TO_KEY[ GLFW_KEY_F3 ] = Event.KEY_F3;
        KEYCODE_TO_KEY[ GLFW_KEY_F4 ] = Event.KEY_F4;
        KEYCODE_TO_KEY[ GLFW_KEY_F5 ] = Event.KEY_F5;
        KEYCODE_TO_KEY[ GLFW_KEY_F6 ] = Event.KEY_F6;
        KEYCODE_TO_KEY[ GLFW_KEY_F7 ] = Event.KEY_F7;
        KEYCODE_TO_KEY[ GLFW_KEY_F8 ] = Event.KEY_F8;
        KEYCODE_TO_KEY[ GLFW_KEY_F9 ] = Event.KEY_F9;
        KEYCODE_TO_KEY[ GLFW_KEY_F10 ] = Event.KEY_F10;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_0 ] = Event.KEY_NUMPAD0;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_1 ] = Event.KEY_NUMPAD1;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_2 ] = Event.KEY_NUMPAD2;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_3 ] = Event.KEY_NUMPAD3;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_4 ] = Event.KEY_NUMPAD4;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_5 ] = Event.KEY_NUMPAD5;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_6 ] = Event.KEY_NUMPAD6;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_7 ] = Event.KEY_NUMPAD7;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_8 ] = Event.KEY_NUMPAD8;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_9 ] = Event.KEY_NUMPAD9;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_DECIMAL ] = Event.KEY_DECIMAL;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_DIVIDE ] = Event.KEY_DIVIDE;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_MULTIPLY ] = Event.KEY_MULTIPLY;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_SUBTRACT ] = Event.KEY_SUBTRACT;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_ADD ] = Event.KEY_ADD;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_ENTER ] = Event.KEY_NUMPADENTER;
        KEYCODE_TO_KEY[ GLFW_KEY_KP_EQUAL ] = Event.KEY_NUMPADEQUALS;
        KEYCODE_TO_KEY[ GLFW_KEY_LEFT_SHIFT ] = Event.KEY_LSHIFT;
        KEYCODE_TO_KEY[ GLFW_KEY_LEFT_CONTROL ] = Event.KEY_LCONTROL;
        KEYCODE_TO_KEY[ GLFW_KEY_LEFT_ALT ] = Event.KEY_LMENU;
        KEYCODE_TO_KEY[ GLFW_KEY_LEFT_SUPER ] = Event.KEY_LMETA;
        KEYCODE_TO_KEY[ GLFW_KEY_RIGHT_SHIFT ] = Event.KEY_RSHIFT;
        KEYCODE_TO_KEY[ GLFW_KEY_RIGHT_CONTROL ] = Event.KEY_RCONTROL;
        KEYCODE_TO_KEY[ GLFW_KEY_RIGHT_ALT ] = Event.KEY_RMENU;
        KEYCODE_TO_KEY[ GLFW_KEY_RIGHT_SUPER ] = Event.KEY_RMETA;
        KEYCODE_TO_KEY[ GLFW_KEY_MENU ] = Event.KEY_W;
    }

}
