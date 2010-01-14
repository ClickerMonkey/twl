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
package de.matthiasmann.twl.theme;

import de.matthiasmann.twl.Border;
import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.renderer.MouseCursor;
import de.matthiasmann.twl.renderer.Image;
import de.matthiasmann.twl.renderer.Renderer;
import de.matthiasmann.twl.renderer.Texture;
import de.matthiasmann.twl.utils.StateExpression;
import de.matthiasmann.twl.utils.XMLParser;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 *
 * @author Matthias Mann
 */
class ImageManager {

    private final Renderer renderer;
    private final TreeMap<String, Image> images;
    private final TreeMap<String, MouseCursor> cursors;

    private Texture currentTexture;
    
    static final EmptyImage NONE = new EmptyImage(0, 0);
    
    ImageManager(Renderer renderer) {
        this.renderer = renderer;
        this.images = new TreeMap<String, Image>();
        this.cursors = new TreeMap<String, MouseCursor>();

        images.put("none", NONE);
    }

    Image getImage(String name) {
        return images.get(name);
    }

    Image getReferencedImage(XMLParser xmlp) throws XmlPullParserException {
        String ref = xmlp.getAttributeNotNull("ref");
        if(ref.endsWith(".*")) {
            throw xmlp.error("wildcard mapping not allowed");
        }
        return getReferencedImage(xmlp, ref);
    }

    Image getReferencedImage(XMLParser xmlp, String ref) throws XmlPullParserException {
        Image img = images.get(ref);
        if(img == null) {
            throw xmlp.error("referenced image \"" + ref + "\" not found");
        }
        return img;
    }

    MouseCursor getReferencedCursor(XMLParser xmlp, String ref) throws XmlPullParserException {
        MouseCursor cursor = getCursor(ref);
        if(cursor == null) {
            throw xmlp.error("referenced cursor \"" + ref + "\" not found");
        }
        return cursor;
    }

    Map<String, Image> getImages(String ref, String name) {
        return ParserUtil.resolve(images, ref, name);
    }

    public MouseCursor getCursor(String name) {
        return cursors.get(name);
    }

    Map<String, MouseCursor> getCursors(String ref, String name) {
        return ParserUtil.resolve(cursors, ref, name);
    }

    void parseTextures(XMLParser xmlp, URL baseUrl) throws XmlPullParserException, IOException {
        xmlp.require(XmlPullParser.START_TAG, null, "textures");
        String fmt = xmlp.getAttributeValue(null, "format");
        String filter = xmlp.getAttributeValue(null, "filter");
        String fileName = xmlp.getAttributeNotNull("file");

        try {
            Texture texture = renderer.loadTexture(new URL(baseUrl, fileName), fmt, filter);
            if(texture == null) {
                throw new NullPointerException("loadTexture returned null");
            }
            this.currentTexture = texture;

            try {
                xmlp.nextTag();
                while(!xmlp.isEndTag()) {
                    String name = xmlp.getAttributeNotNull("name");
                    ParserUtil.checkNameNotEmpty(name, xmlp);
                    if(images.containsKey(name)) {
                        throw xmlp.error("image \"" + name + "\" already defined");
                    }
                    String tagName = xmlp.getName();
                    if("cursor".equals(xmlp.getName())) {
                        parseCursor(xmlp, name, texture);
                    } else {
                        Image image = parseImage(xmlp, tagName);
                        images.put(name, image);
                    }
                    xmlp.require(XmlPullParser.END_TAG, null, tagName);
                    xmlp.nextTag();
                }
            } finally {
                texture.themeLoadingDone();
                currentTexture = null;
            }
        } catch (Exception ex) {
            throw xmlp.error("Unable to load texture: " + fileName, ex);
        }
    }

    private Border getBorder(Image image, Border border) {
        if(border == null && (image instanceof HasBorder)) {
            border = ((HasBorder)image).getBorder();
        }
        return border;
    }

    private void parseCursor(XMLParser xmlp, String name, Texture texture) throws IOException, XmlPullParserException {
        String ref = xmlp.getAttributeValue(null, "ref");
        MouseCursor cursor;
        if(ref != null) {
            cursor = cursors.get(ref);
        } else {
            ImageParams imageParams = new ImageParams();
            parseRectFromAttribute(xmlp, imageParams);
            int hotSpotX = xmlp.parseIntFromAttribute("hotSpotX");
            int hotSpotY = xmlp.parseIntFromAttribute("hotSpotY");
            cursor = texture.createCursor(imageParams.x, imageParams.y, imageParams.w, imageParams.h, hotSpotX, hotSpotY);
        }
        if(cursor != null) {
            cursors.put(name, cursor);
        }
        xmlp.nextTag();
    }

    private Image parseImage(XMLParser xmlp, String tagName) throws XmlPullParserException, IOException {
        ImageParams params = new ImageParams();
        params.condition = ParserUtil.parseCondition(xmlp);
        return parseImageNoCond(xmlp, tagName, params);
    }

    private Image parseImageNoCond(XMLParser xmlp, String tagName, ImageParams params) throws XmlPullParserException, IOException {
        params.tintColor = ParserUtil.parseColorFromAttribute(xmlp, "tint", null);
        params.border = ParserUtil.parseBorderFromAttribute(xmlp, "border");
        params.inset = ParserUtil.parseBorderFromAttribute(xmlp, "inset");
        params.repeatX = xmlp.parseBoolFromAttribute("repeatX", false);
        params.repeatY = xmlp.parseBoolFromAttribute("repeatY", false);
        params.sizeOverwriteH = xmlp.parseIntFromAttribute("sizeOverwriteH", -1);
        params.sizeOverwriteV = xmlp.parseIntFromAttribute("sizeOverwriteV", -1);
        params.center = xmlp.parseBoolFromAttribute("center", false);
        
        Image image = parseImageDelegate(xmlp, tagName, params);
        return adjustImage(image, params);
    }

    private Image adjustImage(Image image, ImageParams params) {
        Border border = getBorder(image, params.border);
        if(params.tintColor != null && !Color.WHITE.equals(params.tintColor)) {
            image = image.createTintedVersion(params.tintColor);
        }
        if(params.repeatX || params.repeatY) {
            image = new RepeatImage(image, border, params.repeatX, params.repeatY);
        }
        Border imgBorder = getBorder(image, null);
        if((border != null && border != imgBorder) || params.inset != null ||
                params.center || params.condition != null ||
                params.sizeOverwriteH >= 0 || params.sizeOverwriteV >= 0) {
            image = new ImageAdjustments(image, border, params.inset,
                    params.sizeOverwriteH, params.sizeOverwriteV,
                    params.center, params.condition);
        }
        return image;
    }

    private Image parseImageDelegate(XMLParser xmlp, String tagName, ImageParams params) throws XmlPullParserException, IOException {
        if("texture".equals(tagName)) {
            return parseTexture(xmlp, params);
        } else if("hvsplit".equals(tagName)) {
            return parseHVSplit(xmlp, params);
        } else if("hsplit".equals(tagName)) {
            return parseHSplit(xmlp, params);
        } else if("vsplit".equals(tagName)) {
            return parseVSplit(xmlp, params);
        } else if("alias".equals(tagName)) {
            return parseAlias(xmlp);
        } else if("composed".equals(tagName)) {
            return parseComposed(xmlp, params);
        } else if("select".equals(tagName)) {
            return parseStateSelect(xmlp, params);
        } else if("grid".equals(tagName)) {
            return parseGrid(xmlp, params);
        } else if("animation".equals(tagName)) {
            return parseAnimation(xmlp, params);
        } else {
            throw xmlp.error("Unexpected '"+tagName+"'");
        }
    }

    private Image parseComposed(XMLParser xmlp, ImageParams params) throws IOException, XmlPullParserException {
        ArrayList<Image> layers = new ArrayList<Image>();
        xmlp.nextTag();
        while(!xmlp.isEndTag()) {
            xmlp.require(XmlPullParser.START_TAG, null, null);
            String tagName = xmlp.getName();
            Image image = parseImage(xmlp, tagName);
            layers.add(image);
            params.border = getBorder(image, params.border);
            xmlp.require(XmlPullParser.END_TAG, null, tagName);
            xmlp.nextTag();
        }
        switch (layers.size()) {
            case 0:
                return NONE;
            case 1:
                return layers.get(0);
            default:
                return new ComposedImage(
                        layers.toArray(new Image[layers.size()]),
                        params.border);
        }
    }

    private Image parseStateSelect(XMLParser xmlp, ImageParams params) throws IOException, XmlPullParserException {
        ArrayList<Image> stateImages = new ArrayList<Image>();
        ArrayList<StateExpression> conditions = new ArrayList<StateExpression>();
        xmlp.nextTag();
        while(!xmlp.isEndTag()) {
            xmlp.require(XmlPullParser.START_TAG, null, null);
            StateExpression cond = ParserUtil.parseCondition(xmlp);
            String tagName = xmlp.getName();
            Image image = parseImageNoCond(xmlp, tagName, new ImageParams());
            stateImages.add(image);
            params.border = getBorder(image, params.border);
            xmlp.require(XmlPullParser.END_TAG, null, tagName);
            xmlp.nextTag();
            if(cond != null) {
                conditions.add(cond);
            } else {
                break;
            }
        }
        if(conditions.size() < 1) {
            throw xmlp.error("state select image needs atleast 1 condition");
        }
        Image image = new StateSelectImage(
                stateImages.toArray(new Image[stateImages.size()]),
                conditions.toArray(new StateExpression[conditions.size()]),
                params.border);
        return image;
    }

    private Image parseTexture(XMLParser xmlp, ImageParams params) throws IOException, XmlPullParserException {
        parseRectFromAttribute(xmlp, params);
        boolean tiled = xmlp.parseBoolFromAttribute("tiled", false);
        Image image = createImage(xmlp, params.x, params.y, params.w, params.h, params.tintColor, tiled);
        params.tintColor = null;
        if(tiled) {
            params.repeatX = false;
            params.repeatY = false;
        }
        xmlp.nextTag();
        return image;
    }

    private Image parseAlias(XMLParser xmlp) throws XmlPullParserException, XmlPullParserException, IOException {
        Image image = getReferencedImage(xmlp);
        xmlp.nextTag();
        return image;
    }

    private static int[] parseSplit(XMLParser xmlp, String attribName, int width) throws XmlPullParserException {
        try {
            int[] off = new int[4];
            ParserUtil.parseIntArray(xmlp.getAttributeNotNull(attribName), off, 1, 2);
            off[3] = width;
            return off;
        } catch(NumberFormatException ex) {
            throw xmlp.error("Unable to parse", ex);
        }
    }

    private static int[] parseSplit(XMLParser xmlp, String attribName, int left, int right, int width) {
        int[] off = new int[4];
        String splitStr = xmlp.getAttributeValue(null, attribName);
        if(splitStr != null) {
            ParserUtil.parseIntArray(splitStr, off, 1, 2);
        } else {
            off[1] = left;
            off[2] = width - right;
        }
        off[3] = width;
        return off;
    }

    private void parseSubImages(XMLParser xmlp, Image[] textures) throws XmlPullParserException, IOException {
        for(int i=0 ; i<textures.length ; i++) {
            xmlp.require(XmlPullParser.START_TAG, null, null);
            String tagName = xmlp.getName();
            textures[i] = parseImage(xmlp, tagName);
            xmlp.require(XmlPullParser.END_TAG, null, tagName);
            xmlp.nextTag();
        }
    }

    private Image parseGrid(XMLParser xmlp, ImageParams params) throws IOException, XmlPullParserException {
        try {
            int[] weightsX = ParserUtil.parseIntArrayFromAttribute(xmlp, "weightsX");
            int[] weightsY = ParserUtil.parseIntArrayFromAttribute(xmlp, "weightsY");
            Image[] textures = new Image[weightsX.length * weightsY.length];
            xmlp.nextTag();
            parseSubImages(xmlp, textures);
            Image image = new GridImage(textures, weightsX, weightsY, params.border);
            return image;
        } catch(IllegalArgumentException ex) {
            ex.printStackTrace();
            throw xmlp.error("Invalid value", ex);
        }
    }

    private static final int[] SPLIT_WEIGHTS_3 = {0,1,0};
    private static final int[] SPLIT_WEIGHTS_1 = {1};
    
    private Image parseHSplit(XMLParser xmlp, ImageParams params) throws IOException, XmlPullParserException {
        Image[] textures = new Image[3];
        parseRectFromAttribute(xmlp, params);
        int[] xoff;
        if(params.border != null) {
            xoff = parseSplit(xmlp, "splitx", params.border.getBorderLeft(),
                    params.border.getBorderRight(), Math.abs(params.w));
        } else {
            xoff = parseSplit(xmlp, "splitx", Math.abs(params.w));
        }
        for(int h=0 ; h<3 ; h++) {
            int imgW = (xoff[h+1] - xoff[h]) * Integer.signum(params.w);
            textures[h] = createImage(xmlp,
                    params.x+xoff[h], params.y, imgW, params.h,
                    params.tintColor, false);
        }
        params.tintColor = null;
        xmlp.nextTag();
        Image image = new GridImage(textures, SPLIT_WEIGHTS_3, SPLIT_WEIGHTS_1, params.border);
        return image;
    }

    private Image parseVSplit(XMLParser xmlp, ImageParams params) throws IOException, XmlPullParserException {
        Image[] textures = new Image[3];
        parseRectFromAttribute(xmlp, params);
        int[] yoff;
        if(params.border != null) {
            yoff = parseSplit(xmlp, "splity", params.border.getBorderTop(),
                    params.border.getBorderBottom(), Math.abs(params.h));
        } else {
            yoff = parseSplit(xmlp, "splity", Math.abs(params.h));
        }
        for(int v=0 ; v<3 ; v++) {
            int imgH = (yoff[v+1] - yoff[v]) * Integer.signum(params.h);
            textures[v] = createImage(xmlp,
                    params.x, params.y+yoff[v], params.w, imgH,
                    params.tintColor, false);
        }
        params.tintColor = null;
        xmlp.nextTag();
        Image image = new GridImage(textures, SPLIT_WEIGHTS_1, SPLIT_WEIGHTS_3, params.border);
        return image;
    }


    private Image parseHVSplit(XMLParser xmlp, ImageParams params) throws IOException, XmlPullParserException {
        Image[] textures = new Image[9];
        parseRectFromAttribute(xmlp, params);
        boolean noCenter = xmlp.parseBoolFromAttribute("nocenter", false);
        int[] xoff, yoff;
        if(params.border != null) {
            xoff = parseSplit(xmlp, "splitx", params.border.getBorderLeft(), params.border.getBorderRight(), Math.abs(params.w));
            yoff = parseSplit(xmlp, "splity", params.border.getBorderTop(), params.border.getBorderBottom(), Math.abs(params.h));
        } else {
            xoff = parseSplit(xmlp, "splitx", Math.abs(params.w));
            yoff = parseSplit(xmlp, "splity", Math.abs(params.h));
        }
        for(int v=0 ; v<3 ; v++) {
            for(int h=0 ; h<3 ; h++) {
                int imgW = (xoff[h+1] - xoff[h]) * Integer.signum(params.w);
                int imgH = (yoff[v+1] - yoff[v]) * Integer.signum(params.h);
                if(noCenter && h == 1 && v == 1) {
                    textures[v*3+h] = new EmptyImage(imgW, imgH);
                } else {
                    textures[v*3+h] = createImage(xmlp,
                            params.x+xoff[h], params.y+yoff[v], imgW, imgH,
                            params.tintColor, false);
                }
            }
        }
        params.tintColor = null;
        xmlp.nextTag();
        Image image = new GridImage(textures, SPLIT_WEIGHTS_3, SPLIT_WEIGHTS_3, params.border);
        return image;
    }

    private void parseAnimElements(XMLParser xmlp, String tagName, ArrayList<AnimatedImage.Element> frames) throws XmlPullParserException, IOException {
        if("repeat".equals(tagName)) {
            frames.add(parseAnimRepeat(xmlp));
        } else if("frame".equals(tagName)) {
            frames.add(parseAnimFrame(xmlp));
        } else if("frames".equals(tagName)) {
            parseAnimFrames(xmlp, frames);
        } else {
            throw xmlp.unexpected();
        }
    }

    private AnimatedImage.Img parseAnimFrame(XMLParser xmlp) throws XmlPullParserException, IOException {
        int duration = xmlp.parseIntFromAttribute("duration");
        if(duration < 0) {
            throw new IllegalArgumentException("duration must be >= 0 ms");
        }
        Color tint = ParserUtil.parseColorFromAttribute(xmlp, "tint", Color.WHITE);
        Image image = getReferencedImage(xmlp);
        AnimatedImage.Img img = new AnimatedImage.Img(duration, image, tint);
        xmlp.nextTag();
        return img;
    }

    private void parseAnimFrames(XMLParser xmlp, ArrayList<AnimatedImage.Element> frames) throws XmlPullParserException, IOException {
        ImageParams params = new ImageParams();
        parseRectFromAttribute(xmlp, params);
        int duration = xmlp.parseIntFromAttribute("duration");
        if(duration < 1) {
            throw new IllegalArgumentException("duration must be >= 1 ms");
        }
        int count = xmlp.parseIntFromAttribute("count");
        if(count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        Color tint = ParserUtil.parseColorFromAttribute(xmlp, "tint", Color.WHITE);
        int xOffset = xmlp.parseIntFromAttribute("offsetx", 0);
        int yOffset = xmlp.parseIntFromAttribute("offsety", 0);
        if(count > 1 && (xOffset == 0 && yOffset == 0)) {
            throw new IllegalArgumentException("offsets required for multiple frames");
        }
        for(int i=0 ; i<count ; i++) {
            Image image = createImage(xmlp, params.x, params.y, params.w, params.h, Color.WHITE, false);
            AnimatedImage.Img img = new AnimatedImage.Img(duration, image, tint);
            frames.add(img);
            params.x += xOffset;
            params.y += yOffset;
        }

        xmlp.nextTag();
    }

    private AnimatedImage.Repeat parseAnimRepeat(XMLParser xmlp) throws XmlPullParserException, IOException {
        String strRepeatCount = xmlp.getAttributeValue(null, "count");
        int repeatCount = 0;
        if(strRepeatCount != null) {
            repeatCount = Integer.parseInt(strRepeatCount);
            if(repeatCount <= 0) {
                throw new IllegalArgumentException("Invalid repeat count");
            }
        }
        boolean lastRepeatsEndless = false;
        boolean hasWarned = false;
        ArrayList<AnimatedImage.Element> children = new ArrayList<AnimatedImage.Element>();
        xmlp.nextTag();
        while(xmlp.isStartTag()) {
            if(lastRepeatsEndless && !hasWarned) {
                hasWarned = true;
                getLogger().warning("Animation frames after an endless repeat won't be displayed: " + xmlp.getPositionDescription());
            }
            String tagName = xmlp.getName();
            parseAnimElements(xmlp, tagName, children);
            AnimatedImage.Element e = children.get(children.size()-1);
            lastRepeatsEndless =
                    (e instanceof AnimatedImage.Repeat) &&
                    ((AnimatedImage.Repeat)e).repeatCount == 0;
            xmlp.require(XmlPullParser.END_TAG, null, tagName);
            xmlp.nextTag();
        }
        return new AnimatedImage.Repeat(children.toArray(new AnimatedImage.Element[children.size()]), repeatCount);
    }

    private Border getBorder(AnimatedImage.Element e) {
        if(e instanceof AnimatedImage.Repeat) {
            AnimatedImage.Repeat r = (AnimatedImage.Repeat)e;
            for(AnimatedImage.Element c : r.children) {
                Border border = getBorder(c);
                if(border != null) {
                    return border;
                }
            }
        } else if(e instanceof AnimatedImage.Img) {
            AnimatedImage.Img i = (AnimatedImage.Img)e;
            if(i.image instanceof HasBorder) {
                return ((HasBorder)i.image).getBorder();
            }
        }
        return null;
    }

    private Image parseAnimation(XMLParser xmlp, ImageParams params) throws XmlPullParserException, IOException {
        try {
            String timeSource = xmlp.getAttributeNotNull("timeSource");
            AnimatedImage.Repeat root = parseAnimRepeat(xmlp);
            if(params.border == null) {
                params.border = getBorder(root);
            }
            Image image = new AnimatedImage(renderer, root, timeSource, params.border,
                    (params.tintColor == null) ? Color.WHITE : params.tintColor);
            params.tintColor = null;
            return image;
        } catch(IllegalArgumentException ex) {
            throw xmlp.error("Unable to parse", ex);
        }
    }

    private Image createImage(XMLParser xmlp, int x, int y, int w, int h, Color tintColor, boolean tiled) {
        if(w == 0 || h == 0) {
            return new EmptyImage(Math.abs(w), Math.abs(h));
        }
        // adjust position for flip
        if(w < 0) {
            x -= w;
        }
        if(h < 0) {
            y -= h;
        }

        Texture texture = currentTexture;
        if(x >= texture.getWidth() || x+Math.abs(w) <= 0 ||
                y >= texture.getHeight() || y+Math.abs(h) <= 0) {
            getLogger().warning("texture partly outside of file: " + xmlp.getPositionDescription());
        }
        return texture.getImage(x, y, w, h, tintColor, tiled);
    }
    
    private void parseRectFromAttribute(XMLParser xmlp, ImageParams params) throws XmlPullParserException {
        params.x = xmlp.parseIntFromAttribute("x");
        params.y = xmlp.parseIntFromAttribute("y");
        params.w = xmlp.parseIntFromAttribute("width");
        params.h = xmlp.parseIntFromAttribute("height");
    }

    Logger getLogger() {
        return Logger.getLogger(ImageManager.class.getName());
    }
    
    static class ImageParams {
        int x, y, w, h;
        Color tintColor;
        Border border;
        Border inset;
        boolean repeatX;
        boolean repeatY;
        int sizeOverwriteH = -1;
        int sizeOverwriteV = -1;
        boolean center;
        StateExpression condition;
    }
}
