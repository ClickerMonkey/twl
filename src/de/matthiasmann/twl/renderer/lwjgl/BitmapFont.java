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
package de.matthiasmann.twl.renderer.lwjgl;

import de.matthiasmann.twl.HAlignment;
import de.matthiasmann.twl.utils.TextUtil;
import de.matthiasmann.twl.renderer.FontCache;
import de.matthiasmann.twl.utils.XMLParser;
import java.io.IOException;
import java.net.URL;
import org.lwjgl.opengl.GL11;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A Bitmap Font class. Parses the output of AngelCode's BMFont tool.
 * 
 * @author Matthias Mann
 */
public class BitmapFont {

    private static final int LOG2_PAGE_SIZE = 9;
    private static final int PAGE_SIZE = 1 << LOG2_PAGE_SIZE;
    private static final int PAGES = 0x10000 / PAGE_SIZE;

    static class Glyph extends TextureAreaBase {
        short xoffset;
        short yoffset;
        short xadvance;
        byte[][] kerning;

        public Glyph(int x, int y, int width, int height, int texWidth, int texHeight) {
            super(x, y, width, height, texWidth, texHeight);
        }
        
        void draw(int x, int y) {
            drawQuad(x+xoffset, y+yoffset, width, height);
        }
        
        int getKerning(char ch) {
            if(kerning != null) {
                byte[] page = kerning[ch >>> LOG2_PAGE_SIZE];
                if(page != null) {
                    return page[ch & (PAGE_SIZE-1)];
                }
            }
            return 0;
        }
        
        void setKerning(int ch, int value) {
            if(kerning == null) {
                kerning = new byte[PAGES][];
            }
            byte[] page = kerning[ch >>> LOG2_PAGE_SIZE];
            if(page == null) {
                kerning[ch >>> LOG2_PAGE_SIZE] = page = new byte[PAGE_SIZE];
            }
            page[ch & (PAGE_SIZE-1)] = (byte)value;
        }
    }

    private final LWJGLTexture texture;
    private final Glyph[][] glyphs;
    private final int lineHeight;
    private final int baseLine;

    public BitmapFont(LWJGLRenderer renderer, XMLParser xmlp, URL baseUrl) throws XmlPullParserException, IOException {
        xmlp.require(XmlPullParser.START_TAG, null, "font");
        xmlp.nextTag();
        xmlp.require(XmlPullParser.START_TAG, null, "info");
        xmlp.ignoreOtherAttributes();
        xmlp.nextTag();
        xmlp.require(XmlPullParser.END_TAG, null, "info");
        xmlp.nextTag();
        xmlp.require(XmlPullParser.START_TAG, null, "common");
        lineHeight = xmlp.parseIntFromAttribute("lineHeight");
        baseLine = xmlp.parseIntFromAttribute("base");
        if(xmlp.parseIntFromAttribute("pages", 1) != 1) {
            throw new UnsupportedOperationException("multi page fonts not supported");
        }
        if(xmlp.parseIntFromAttribute("packed", 0) != 0) {
            throw new UnsupportedOperationException("packed fonts not supported");
        }
        xmlp.ignoreOtherAttributes();
        xmlp.nextTag();
        xmlp.require(XmlPullParser.END_TAG, null, "common");
        xmlp.nextTag();
        xmlp.require(XmlPullParser.START_TAG, null, "pages");
        xmlp.nextTag();
        xmlp.require(XmlPullParser.START_TAG, null, "page");
        int pageId = Integer.parseInt(xmlp.getAttributeValue(null, "id"));
        if(pageId != 0) {
            throw new UnsupportedOperationException("only page id 0 supported");
        }
        String textureName = xmlp.getAttributeValue(null, "file");
        this.texture = renderer.load(new URL(baseUrl, textureName), LWJGLTexture.Format.ALPHA, LWJGLTexture.Filter.NEAREST);
        xmlp.nextTag();
        xmlp.require(XmlPullParser.END_TAG, null, "page");
        xmlp.nextTag();
        xmlp.require(XmlPullParser.END_TAG, null, "pages");
        xmlp.nextTag();
        xmlp.require(XmlPullParser.START_TAG, null, "chars");
        xmlp.ignoreOtherAttributes();
        xmlp.nextTag();
        
        glyphs = new Glyph[PAGES][];
        while(!xmlp.isEndTag()) {
            xmlp.require(XmlPullParser.START_TAG, null, "char");
            int idx = xmlp.parseIntFromAttribute("id");
            int x = xmlp.parseIntFromAttribute("x");
            int y = xmlp.parseIntFromAttribute("y");
            int w = xmlp.parseIntFromAttribute("width");
            int h = xmlp.parseIntFromAttribute("height");
            if(xmlp.parseIntFromAttribute("page", 0) != 0) {
                throw xmlp.error("Multiple pages not supported");
            }
            if(xmlp.parseIntFromAttribute("chnl", 0) != 0) {
                throw xmlp.error("Multiple channels not supported");
            }
            Glyph g = new Glyph(x, y, w, h, texture.getTexWidth(), texture.getTexHeight());
            g.xoffset = Short.parseShort(xmlp.getAttributeNotNull("xoffset"));
            g.yoffset = Short.parseShort(xmlp.getAttributeNotNull("yoffset"));
            g.xadvance = Short.parseShort(xmlp.getAttributeNotNull("xadvance"));
            if(idx <= Character.MAX_VALUE) {
                Glyph[] page = glyphs[idx / PAGE_SIZE];
                if(page == null) {
                    glyphs[idx / PAGE_SIZE] = page = new Glyph[PAGE_SIZE];
                }
                page[idx & (PAGE_SIZE-1)] = g;
            }
            xmlp.nextTag();
            xmlp.require(XmlPullParser.END_TAG, null, "char");
            xmlp.nextTag();
        }
        
        xmlp.require(XmlPullParser.END_TAG, null, "chars");
        xmlp.nextTag();
        if(xmlp.isStartTag()) {
            xmlp.require(XmlPullParser.START_TAG, null, "kernings");
            xmlp.ignoreOtherAttributes();
            xmlp.nextTag();
            while(!xmlp.isEndTag()) {
                xmlp.require(XmlPullParser.START_TAG, null, "kerning");
                int first = xmlp.parseIntFromAttribute("first");
                int second = xmlp.parseIntFromAttribute("second");
                int amount = xmlp.parseIntFromAttribute("amount");
                if(first >= 0 && first <= Character.MAX_VALUE &&
                        second >= 0 && second <= Character.MAX_VALUE) {
                    Glyph g = getGlyph((char)first);
                    if(g != null) {
                        g.setKerning(second, amount);
                    }
                }
                xmlp.nextTag();
                xmlp.require(XmlPullParser.END_TAG, null, "kerning");
                xmlp.nextTag();
            }
            xmlp.require(XmlPullParser.END_TAG, null, "kernings");
            xmlp.nextTag();
        }
        xmlp.require(XmlPullParser.END_TAG, null, "font");
    }

    public static BitmapFont loadFont(LWJGLRenderer renderer, URL url) throws IOException {
        try {
            XMLParser xmlp = new XMLParser(url);
            try {
                xmlp.require(XmlPullParser.START_DOCUMENT, null, null);
                xmlp.nextTag();
                return new BitmapFont(renderer, xmlp, url);
            } finally {
                xmlp.close();
            }
        } catch (XmlPullParserException ex) {
            throw (IOException)(new IOException().initCause(ex));
        }
    }
    
    public int getBaseLine() {
        return baseLine;
    }

    public int getLineHeight() {
        return lineHeight;
    }

    public int getSpaceWidth() {
        Glyph g = getGlyph(' ');
        if(g != null) {
            return g.xadvance + g.width;
        } else {
            return 1;
        }
    }

    public void destroy() {
        texture.destroy();
    }

    private Glyph getGlyph(char ch) {
        Glyph[] page = glyphs[ch / PAGE_SIZE];
        if(page != null) {
            return page[ch & (PAGE_SIZE-1)];
        }
        return null;
    }
    
    public int computeTextWidth(CharSequence str, int start, int end) {
        int width = 0;
        Glyph lastGlyph = null;
        while(start < end) {
            lastGlyph = getGlyph(str.charAt(start++));
            if(lastGlyph != null) {
                width = lastGlyph.xadvance;
                break;
            }
        }
        while(start < end) {
            char ch = str.charAt(start++);
            Glyph g = getGlyph(ch);
            if(g != null) {
                width += lastGlyph.getKerning(ch);
                lastGlyph = g;
                width += g.xadvance;
            }
        }
        return width;
    }

    public int computeVisibleGlpyhs(CharSequence str, int start, int end, int availWidth) {
        int index = start;
        int width = 0;
        Glyph lastGlyph = null;
        for(; index < end ; index++) {
            char ch = str.charAt(index);
            Glyph g = getGlyph(ch);
            if(g != null) {
                if(lastGlyph != null) {
                    width += lastGlyph.getKerning(ch);
                }
                lastGlyph = g;
                if(width + g.width + g.xoffset > availWidth) {
                    break;
                }
                width += g.xadvance;
            }
        }
        return index - start;
    }
    
    protected int drawText(int x, int y, CharSequence str, int start, int end) {
        int startX = x;
        Glyph lastGlyph = null;
        while(start < end) {
            lastGlyph = getGlyph(str.charAt(start++));
            if(lastGlyph != null) {
                lastGlyph.draw(x, y);
                x += lastGlyph.xadvance;
                break;
            }
        }
        while(start < end) {
            char ch = str.charAt(start++);
            Glyph g = getGlyph(ch);
            if(g != null) {
                x += lastGlyph.getKerning(ch);
                lastGlyph = g;
                g.draw(x, y);
                x += g.xadvance;
            }
        }
        return x - startX;
    }
    
    protected int drawMultiLineText(int x, int y, CharSequence str, int width, HAlignment align) {
        int start = 0;
        int startY = y;
        while(start < str.length()) {
            int lineEnd = TextUtil.indexOf(str, '\n', start);
            int xoff = 0;
            if(align != HAlignment.LEFT) {
                int lineWidth = computeTextWidth(str, start, lineEnd);
                xoff = width - lineWidth;
                if(align == HAlignment.CENTER) {
                    xoff /= 2;
                }
            }
            drawText(x + xoff, y, str, start, lineEnd);
            start = lineEnd + 1;
            y += lineHeight;
        }
        return y - startY;
    }
    
    public int computeMultiLineTextWidth(CharSequence str) {
        int start = 0;
        int width = 0;
        while(start < str.length()) {
            int lineEnd = TextUtil.indexOf(str, '\n', start);
            int lineWidth = computeTextWidth(str, start, lineEnd);
            width = Math.max(width, lineWidth);
            start = lineEnd + 1;
        }
        return width;
    }

    public FontCache cacheMultiLineText(LWJGLFontCache cache, CharSequence str, int width, HAlignment align) {
        if(cache.startCompile()) {
            int height = 0;
            try {
                if(prepare()) {
                    try {
                        height = drawMultiLineText(0, 0, str, width, align);
                    } finally {
                        cleanup();
                    }
                }
            } finally {
                cache.endCompile(width, height);
            }
            return cache;
        }
        return null;
    }

    public FontCache cacheText(LWJGLFontCache cache, CharSequence str, int start, int end) {
        if(cache.startCompile()) {
            int width = 0;
            try {
                if(prepare()) {
                    try {
                        width = drawText(0, 0, str, start, end);
                    } finally {
                        cleanup();
                    }
                }
            } finally {
                cache.endCompile(width, getLineHeight());
            }
            return cache;
        }
        return null;
    }

    protected boolean prepare() {
        if(texture.bind()) {
            GL11.glBegin(GL11.GL_QUADS);
            return true;
        }
        return false;
    }

    protected void cleanup() {
        GL11.glEnd();
    }
    
}
