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

import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.Rect;
import de.matthiasmann.twl.renderer.CacheContext;
import de.matthiasmann.twl.renderer.DynamicImage;
import de.matthiasmann.twl.renderer.FontParameter;
import de.matthiasmann.twl.renderer.MouseCursor;
import de.matthiasmann.twl.renderer.Font;
import de.matthiasmann.twl.renderer.LineRenderer;
import de.matthiasmann.twl.renderer.Renderer;
import de.matthiasmann.twl.renderer.Texture;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.EXTTextureRectangle;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/**
 * A renderer using only GL11 features
 * 
 * @author Matthias Mann
 */
public class LWJGLRenderer implements Renderer, LineRenderer {

    private final IntBuffer ib16;
    private final int maxTextureSize;

    private int width;
    private int height;
    private boolean hasScissor;
    private final TintState tintStateRoot;
    private final Cursor emptyCursor;
    private boolean useQuadsForLines;
    private boolean useSWMouseCursors;
    private SWCursor swCursor;
    private LWJGLCacheContext cacheContext;

    final ArrayList<Integer> textureDLs;
    final ArrayList<LWJGLDynamicImage> dynamicImages;
    TintState tintState;

    public LWJGLRenderer() throws LWJGLException {
        this.ib16 = BufferUtils.createIntBuffer(16);
        this.textureDLs = new ArrayList<Integer>();
        this.dynamicImages = new ArrayList<LWJGLDynamicImage>();
        this.tintStateRoot = new TintState();
        this.tintState = tintStateRoot;
        syncViewportSize();

        GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE, ib16);
        maxTextureSize = ib16.get(0);
        
        int minCursorSize = Cursor.getMinCursorSize();
        IntBuffer tmp = BufferUtils.createIntBuffer(minCursorSize * minCursorSize);
        emptyCursor = new Cursor(minCursorSize, minCursorSize,
                minCursorSize/2, minCursorSize/2, 1, tmp, null);
    }

    public boolean isUseQuadsForLines() {
        return useQuadsForLines;
    }

    public void setUseQuadsForLines(boolean useQuadsForLines) {
        this.useQuadsForLines = useQuadsForLines;
    }

    public boolean isUseSWMouseCursors() {
        return useSWMouseCursors;
    }

    public void setUseSWMouseCursors(boolean useSWMouseCursors) {
        this.useSWMouseCursors = useSWMouseCursors;
    }

    public CacheContext createNewCacheContext() {
        return new LWJGLCacheContext(this);
    }

    private LWJGLCacheContext activeCacheContext() {
        if(cacheContext == null) {
            setActiveCacheContext(createNewCacheContext());
        }
        return cacheContext;
    }

    public CacheContext getActiveCacheContext() {
        return activeCacheContext();
    }

    public void setActiveCacheContext(CacheContext cc) throws IllegalStateException {
        if(cc == null) {
            throw new NullPointerException();
        }
        if(!cc.isValid()) {
            throw new IllegalStateException("CacheContext is invalid");
        }
        if(!(cc instanceof LWJGLCacheContext)) {
            throw new IllegalArgumentException("CacheContext object not from this renderer");
        }
        LWJGLCacheContext lwjglCC = (LWJGLCacheContext)cc;
        if(lwjglCC.renderer != this) {
            throw new IllegalArgumentException("CacheContext object not from this renderer");
        }
        this.cacheContext = lwjglCC;
        try {
            for(int id : textureDLs) {
                GL11.glDeleteLists(id, 1);
            }
        } finally {
            textureDLs.clear();
        }
    }
    
    public void syncViewportSize() {
        ib16.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib16);
        width = ib16.get(2);
        height = ib16.get(3);
    }

    /**
     * Setup GL to start rendering the GUI. It assumes default GL state.
     */
    public void startRenderering() {
        hasScissor = false;
        tintState = tintStateRoot;
        
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_TRANSFORM_BIT|GL11.GL_HINT_BIT|
                GL11.GL_COLOR_BUFFER_BIT|GL11.GL_SCISSOR_BIT|GL11.GL_LINE_BIT|GL11.GL_TEXTURE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    }

    public void endRendering() {
        if(swCursor != null) {
            swCursor.render(Mouse.getX(), height-Mouse.getY());
        }
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
    
    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public Font loadFont(URL baseUrl, Map<String, String> parameter, Collection<FontParameter> conditionalParameter) throws IOException {
        String fileName = parameter.get("filename");
        if(fileName == null) {
            throw new IllegalArgumentException("filename parameter required");
        }
        URL url = new URL(baseUrl, fileName);
        BitmapFont bmFont = activeCacheContext().loadBitmapFont(url);
        return new LWJGLFont(this, bmFont, parameter, conditionalParameter);
    }

    public Texture loadTexture(URL url, String formatStr, String filterStr) throws IOException {
        LWJGLTexture.Format format = LWJGLTexture.Format.RGBA;
        LWJGLTexture.Filter filter = LWJGLTexture.Filter.LINEAR;
        if(formatStr != null) {
            try {
                format = LWJGLTexture.Format.valueOf(formatStr.toUpperCase());
            } catch(IllegalArgumentException ex) {
                getLogger().warning("Unknown texture format: " + formatStr);
            }
        }
        if(filterStr != null) {
            try {
                filter = LWJGLTexture.Filter.valueOf(filterStr.toUpperCase());
            } catch(IllegalArgumentException ex) {
                getLogger().warning("Unknown texture filter: " + filterStr);
            }
        }
        return load(url, format, filter);
    }

    public LineRenderer getLineRenderer() {
        return this;
    }

    public DynamicImage createDynamicImage(int width, int height) {
        if(width <= 0) {
            throw new IllegalArgumentException("width");
        }
        if(height <= 0) {
            throw new IllegalArgumentException("height");
        }
        if(width > maxTextureSize || height > maxTextureSize) {
            return null;
        }

        boolean useTextureRectangle = GLContext.getCapabilities().GL_EXT_texture_rectangle;

        int proxyTarget = useTextureRectangle ?
            EXTTextureRectangle.GL_PROXY_TEXTURE_RECTANGLE_EXT : GL11.GL_PROXY_TEXTURE_2D;

        GL11.glTexImage2D(proxyTarget, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
        ib16.clear();
        GL11.glGetTexLevelParameter(proxyTarget, 0, GL11.GL_TEXTURE_WIDTH, ib16);
        if(ib16.get(0) != width) {
            return null;
        }

        int target = useTextureRectangle ?
            EXTTextureRectangle.GL_TEXTURE_RECTANGLE_EXT : GL11.GL_TEXTURE_2D;
        int id = glGenTexture();

        GL11.glBindTexture(target, id);
        GL11.glTexImage2D(target, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

        LWJGLDynamicImage image = new LWJGLDynamicImage(this, target, id, width, height, Color.WHITE);
        dynamicImages.add(image);
        return image;
    }

    public void setClipRect(Rect rect) {
        if(rect == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            hasScissor = false;
        } else {
            GL11.glScissor(rect.getX(), getHeight() - rect.getBottom(), rect.getWidth(), rect.getHeight());
            if(!hasScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                hasScissor = true;
            }
        }
    }

    public void setCursor(MouseCursor cursor) {
        try {
            if(Mouse.isInsideWindow()) {
                swCursor = null;
                if(cursor instanceof LWJGLCursor) {
                    Mouse.setNativeCursor(((LWJGLCursor)cursor).cursor);
                } else if(cursor instanceof SWCursor) {
                    Mouse.setNativeCursor(emptyCursor);
                    swCursor = (SWCursor)cursor;
                } else {
                    Mouse.setNativeCursor(null);
                }
            }
        } catch(LWJGLException ex) {
            ex.printStackTrace();
        }
    }

    public LWJGLTexture load(URL textureUrl, LWJGLTexture.Format fmt, LWJGLTexture.Filter filter) throws IOException {
        if(textureUrl == null) {
            throw new NullPointerException("textureUrl");
        }
        return activeCacheContext().loadTexture(textureUrl, fmt, filter);
    }

    public void pushGlobalTintColor(float r, float g, float b, float a) {
        tintState = tintState.next(r, g, b, a);
    }

    public void popGlobalTintColor() {
        tintState = tintState.prev;
    }

    public void drawLine(float[] pts, int numPts, float width, Color color, boolean drawAsLoop) {
        if(numPts*2 > pts.length) {
            throw new ArrayIndexOutOfBoundsException(numPts*2);
        }
        if(numPts >= 2) {
            tintState.setColor(color);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            if(useQuadsForLines) {
                drawLinesAsQuads(numPts, pts, width, drawAsLoop);
            } else {
                drawLinesAsLines(numPts, pts, width, drawAsLoop);
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }
    
    private void drawLinesAsLines(int numPts, float[] pts, float width, boolean drawAsLoop) {
        GL11.glLineWidth(width);
        GL11.glBegin(drawAsLoop ? GL11.GL_LINE_LOOP : GL11.GL_LINE_STRIP);
        for(int i=0 ; i<numPts ; i++) {
            GL11.glVertex2f(pts[i*2+0], pts[i*2+1]);
        }
        GL11.glEnd();
    }

    private void drawLinesAsQuads(int numPts, float[] pts, float width, boolean drawAsLoop) {
        width *= 0.5f;
        GL11.glBegin(GL11.GL_QUADS);
        for(int i = 1 ; i < numPts ; i++) {
            drawLineAsQuad(pts[i * 2 - 2], pts[i * 2 - 1], pts[i * 2 + 0], pts[i * 2 + 1], width);
        }
        if(drawAsLoop) {
            int idx = numPts * 2;
            drawLineAsQuad(pts[idx], pts[idx + 1], pts[0], pts[1], width);
        }
        GL11.glEnd();
    }

    private static void drawLineAsQuad(float x0, float y0, float x1, float y1, float w) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float l = (float)Math.sqrt(dx*dx + dy*dy) / w;
        dx /= l;
        dy /= l;
        GL11.glVertex2f(x0 - dx + dy, y0 - dy - dx);
        GL11.glVertex2f(x0 - dx - dy, y0 - dy + dx);
        GL11.glVertex2f(x1 + dx - dy, y1 + dy + dx);
        GL11.glVertex2f(x1 + dx + dy, y1 + dy - dx);
    }

    protected void getTintedColor(Color color, float[] result) {
        result[0] = tintState.r*(color.getR()&255);
        result[1] = tintState.g*(color.getG()&255);
        result[2] = tintState.b*(color.getB()&255);
        result[3] = tintState.a*(color.getA()&255);
    }

    Logger getLogger() {
        return Logger.getLogger(LWJGLRenderer.class.getName());
    }

    int glGenTexture() {
        ib16.clear().limit(1);
        GL11.glGenTextures(ib16);
        return ib16.get(0);
    }

    void glDeleteTexture(int id) {
        ib16.clear();
        ib16.put(id).flip();
        GL11.glDeleteTextures(ib16);
    }
    
    static class TintState {
        private static final float ONE_OVER_255 = 1f / 255f;

        final TintState prev;
        TintState next;
        private float r,g,b,a;

        public TintState() {
            this.prev = this;
            this.r = ONE_OVER_255;
            this.g = ONE_OVER_255;
            this.b = ONE_OVER_255;
            this.a = ONE_OVER_255;
        }

        TintState(TintState prev) {
            this.prev = prev;
        }

        TintState next(float r, float g, float b, float a) {
            if(next == null) {
                next = new TintState(this);
            }
            next.r = this.r * r;
            next.g = this.g * g;
            next.b = this.b * b;
            next.a = this.a * a;
            return next;
        }

        void setColor(Color color) {
            GL11.glColor4f(
                    r*(color.getR()&255),
                    g*(color.getG()&255),
                    b*(color.getB()&255),
                    a*(color.getA()&255));
        }
    }
}
