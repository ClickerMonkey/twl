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

import de.matthiasmann.twl.textarea.TextAreaModel;
import de.matthiasmann.twl.textarea.TextAreaModel.Clear;
import de.matthiasmann.twl.textarea.TextAreaModel.Element;
import de.matthiasmann.twl.textarea.TextAreaModel.FloatPosition;
import de.matthiasmann.twl.textarea.TextAreaModel.TextElement;
import de.matthiasmann.twl.renderer.Font;
import de.matthiasmann.twl.renderer.FontCache;
import de.matthiasmann.twl.renderer.Image;
import de.matthiasmann.twl.renderer.MouseCursor;
import de.matthiasmann.twl.textarea.CSSStyle;
import de.matthiasmann.twl.textarea.Style;
import de.matthiasmann.twl.textarea.StyleAttribute;
import de.matthiasmann.twl.textarea.StyleClassResolver;
import de.matthiasmann.twl.textarea.Value;
import de.matthiasmann.twl.utils.CallbackSupport;
import de.matthiasmann.twl.utils.TextUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A text area dor rendering complex text. Supports embedded images,
 * bullet point lists, hyper links, multiple fonts, block text,
 * embedded widgets and floating elements.
 *
 * It uses a simplified HTML/CSS model.
 * 
 * @author Matthias Mann
 */
public class TextArea extends Widget {

    public interface WidgetResolver {
        public Widget resolveWidget(String name, String param);
    }

    public interface ImageResolver {
        public Image resolveImage(String name);
    }

    public interface Callback {
        /**
         * Called when a link has been clicked
         * @param href the href of the link
         */
        public void handleLinkClicked(String href);
    }

    public static final String STATE_HOVER = "hover";
    
    private final HashMap<String, Widget> widgets;
    private final HashMap<String, WidgetResolver> widgetResolvers;
    private final HashMap<String, Image> userImages;
    private final ArrayList<ImageResolver> imageResolvers;

    StyleClassResolver styleClassResolver;
    private final Runnable modelCB;
    private TextAreaModel model;
    private ParameterMap fonts;
    private ParameterMap images;
    private Font defaultFont;
    private Callback[] callbacks;
    private MouseCursor mouseCursorNormal;
    private MouseCursor mouseCursorLink;

    final LClip layoutRoot;
    final ArrayList<LImage> allBGImages;
    private boolean inLayoutCode;
    private boolean forceRelayout;

    private int lastMouseX;
    private int lastMouseY;
    private boolean lastMouseInside;
    private LElement curLElementUnderMouse;

    public TextArea() {
        this.widgets = new HashMap<String, Widget>();
        this.widgetResolvers = new HashMap<String, WidgetResolver>();
        this.userImages = new HashMap<String, Image>();
        this.imageResolvers = new ArrayList<ImageResolver>();
        this.layoutRoot = new LClip(null);
        this.allBGImages = new ArrayList<LImage>();
        
        this.modelCB = new Runnable() {
            public void run() {
                forceRelayout();
            }
        };
    }

    public TextArea(TextAreaModel model) {
        this();
        setModel(model);
    }

    public TextAreaModel getModel() {
        return model;
    }

    public void setModel(TextAreaModel model) {
        if(this.model != null) {
            this.model.removeCallback(modelCB);
        }
        this.model = model;
        if(model != null) {
            model.addCallback(modelCB);
        }
        forceRelayout();
    }

    public void registerWidget(String name, Widget widget) {
        if(name == null) {
            throw new NullPointerException("name");
        }
        if(widget.getParent() != null) {
            throw new IllegalArgumentException("Widget must not have a parent");
        }
        if(widgets.containsKey(name) || widgetResolvers.containsKey(name)) {
            throw new IllegalArgumentException("widget name already in registered");
        }
        if(widgets.containsValue(widget)) {
            throw new IllegalArgumentException("widget already registered");
        }
        widgets.put(name, widget);
    }

    public void registerWidgetResolver(String name, WidgetResolver resolver) {
        if(name == null) {
            throw new NullPointerException("name");
        }
        if(resolver == null) {
            throw new NullPointerException("resolver");
        }
        if(widgets.containsKey(name) || widgetResolvers.containsKey(name)) {
            throw new IllegalArgumentException("widget name already in registered");
        }
        widgetResolvers.put(name, resolver);
    }

    public void unregisterWidgetResolver(String name) {
        if(name == null) {
            throw new NullPointerException("name");
        }
        widgetResolvers.remove(name);
    }

    public void unregisterWidget(String name) {
        if(name == null) {
            throw new NullPointerException("name");
        }
        Widget w = widgets.get(name);
        if(w != null) {
            int idx = getChildIndex(w);
            if(idx >= 0) {
                super.removeChild(idx);
                forceRelayout();
            }
        }
    }

    public void unregisterAllWidgets() {
        widgets.clear();
        super.removeAllChildren();
        forceRelayout();
    }

    public void registerImage(String name, Image image) {
        if(name == null) {
            throw new NullPointerException("name");
        }
        userImages.put(name, image);
    }

    public void registerImageResolver(ImageResolver resolver) {
        if(resolver == null) {
            throw new NullPointerException("resolver");
        }
        if(!imageResolvers.contains(resolver)) {
            imageResolvers.add(resolver);
        }
    }

    public void unregisterImage(String name) {
        userImages.remove(name);
    }

    public void unregisterImageResolver(ImageResolver imageResolver) {
        imageResolvers.remove(imageResolver);
    }

    public void addCallback(Callback cb) {
        callbacks = CallbackSupport.addCallbackToList(callbacks, cb, Callback.class);
    }

    public void removeCallback(Callback cb) {
        callbacks = CallbackSupport.removeCallbackFromList(callbacks, cb);
    }

    @Override
    protected void applyTheme(ThemeInfo themeInfo) {
        super.applyTheme(themeInfo);
        applyThemeTextArea(themeInfo);
    }

    protected void applyThemeTextArea(ThemeInfo themeInfo) {
        fonts = themeInfo.getParameterMap("fonts");
        images = themeInfo.getParameterMap("images");
        defaultFont = themeInfo.getFont("font");
        mouseCursorNormal = themeInfo.getMouseCursor("mouseCursor");
        mouseCursorLink = themeInfo.getMouseCursor("mouseCursor.link");
        styleClassResolver = new StyleClassResolverImpl(themeInfo.getParameterMap("classes"));
        forceRelayout();
    }

    @Override
    public void insertChild(Widget child, int index) {
        throw new UnsupportedOperationException("use registerWidget");
    }

    @Override
    public void removeAllChildren() {
        throw new UnsupportedOperationException("use registerWidget");
    }

    @Override
    public Widget removeChild(int index) {
        throw new UnsupportedOperationException("use registerWidget");
    }

    @Override
    public int getPreferredInnerWidth() {
        return getInnerWidth();
    }

    @Override
    public int getPreferredInnerHeight() {
        validateLayout();
        return layoutRoot.height;
    }
    
    @Override
    public int getPreferredWidth() {
        int maxWidth = getMaxWidth();
        if(maxWidth > 0) {
            return maxWidth;
        }
        return computeSize(getMinWidth(), super.getPreferredWidth(), maxWidth);
    }

    @Override
    public void setMaxSize(int width, int height) {
        if(width != getMaxWidth()) {
            invalidateLayout();
        }
        super.setMaxSize(width, height);
    }

    @Override
    public void setMinSize(int width, int height) {
        if(width != getMinWidth()) {
            invalidateLayout();
        }
        super.setMinSize(width, height);
    }
    
    @Override
    protected void layout() {
        int targetWidth = computeSize(getMinWidth(), getWidth(), getMaxWidth());
        targetWidth -= getBorderHorizontal();

        //System.out.println(this+" minWidth="+getMinWidth()+" width="+getWidth()+" maxWidth="+getMaxWidth());
        
        // only recompute the layout when it has changed
        if(layoutRoot.width != targetWidth || forceRelayout) {
            layoutRoot.width = targetWidth;
            this.inLayoutCode = true;
            this.forceRelayout = false;

            clearLayout();
            Box box = new Box(layoutRoot, 0, 0, targetWidth);

            try {
                if(model != null) {
                    layoutElements(box, model);
                    
                    // finish the last line
                    box.nextLine(false);
                    
                    // finish floaters
                    box.clearFloater(Clear.BOTH);

                    // set position & size of all widget elements
                    layoutRoot.adjustWidget(getInnerX(), getInnerY());
                }
                updateMouseHover();
            } finally {
                inLayoutCode = false;
            }

            if(layoutRoot.height != box.curY) {
                layoutRoot.height = box.curY;
                // call outside of inLayoutCode range
                invalidateLayout();
            }
        }
    }

    @Override
    protected void paintWidget(GUI gui) {
        final ArrayList<LImage> bi = allBGImages;
        final int innerX = getInnerX();
        final int innerY = getInnerY();
        final AnimationState as = getAnimationState();

        for(int i=0,n=bi.size() ; i<n ; i++) {
            bi.get(i).draw(innerX, innerY, as);
        }

        layoutRoot.draw(innerX, innerY, as);
    }

    @Override
    protected void sizeChanged() {
        if(!inLayoutCode) {
            invalidateLayout();
        }
    }

    @Override
    protected void childAdded(Widget child) {
        // always ignore
    }

    @Override
    protected void childRemoved(Widget exChild) {
        // always ignore
    }

    @Override
    protected void allChildrenRemoved() {
        // always ignore
    }

    @Override
    public void destroy() {
        super.destroy();
        clearLayout();
        forceRelayout();
    }

    @Override
    protected boolean handleEvent(Event evt) {
        if(super.handleEvent(evt)) {
            return true;
        }

        if(evt.isMouseEvent()) {
            lastMouseInside = isMouseInside(evt);
            lastMouseX = evt.getMouseX();
            lastMouseY = evt.getMouseY();
            updateMouseHover();

            if(evt.getType() == Event.Type.MOUSE_WHEEL) {
                return false;
            }

            if(evt.getType() == Event.Type.MOUSE_CLICKED) {
                if(curLElementUnderMouse != null && (curLElementUnderMouse.element instanceof TextAreaModel.LinkElement)) {
                    String href = ((TextAreaModel.LinkElement)curLElementUnderMouse.element).getHREF();
                    if(callbacks != null) {
                        for(Callback l : callbacks) {
                            l.handleLinkClicked(href);
                        }
                    }
                }
            }

            return true;
        }

        return false;
    }

    @Override
    protected Object getTooltipContentAt(int mouseX, int mouseY) {
        if(curLElementUnderMouse != null) {
            if(curLElementUnderMouse.element instanceof TextAreaModel.ImageElement) {
                return ((TextAreaModel.ImageElement)curLElementUnderMouse.element).getToolTip();
            }
        }
        return super.getTooltipContentAt(mouseX, mouseY);
    }

    private void updateMouseHover() {
        LElement le = null;
        if(lastMouseInside) {
            le = layoutRoot.find(lastMouseX - getInnerX(), lastMouseY - getInnerY());
        }
        if(curLElementUnderMouse != le) {
            curLElementUnderMouse = le;
            updateTooltip();
        }
        
        if(le != null && le.element instanceof TextAreaModel.LinkElement) {
            setMouseCursor(mouseCursorLink);
        } else {
            setMouseCursor(mouseCursorNormal);
        }
    }

    void forceRelayout() {
        forceRelayout = true;
        invalidateLayout();
    }
    
    private void clearLayout() {
        layoutRoot.destroy();
        allBGImages.clear();
        super.removeAllChildren();
    }

    private void layoutElements(Box box, Iterable<TextAreaModel.Element> elements) {
        for(TextAreaModel.Element e : elements) {
            box.clearFloater(e.getStyle().get(StyleAttribute.CLEAR, styleClassResolver));
            
            if(e instanceof TextAreaModel.TextElement) {
                layoutTextElement(box, (TextAreaModel.TextElement)e);
            } else if(e instanceof TextAreaModel.ImageElement) {
                layoutImageElement(box, (TextAreaModel.ImageElement)e);
            } else if(e instanceof TextAreaModel.WidgetElement) {
                layoutWidgetElement(box, (TextAreaModel.WidgetElement)e);
            } else if(e instanceof TextAreaModel.ListElement) {
                layoutListElement(box, (TextAreaModel.ListElement)e);
            } else if(e instanceof TextAreaModel.BlockElement) {
                layoutBlockElement(box, (TextAreaModel.BlockElement)e);
            } else {
                Logger.getLogger(TextArea.class.getName()).log(Level.SEVERE,
                        "Unknown Element subclass: " + e.getClass());
            }
        }
    }
    
    private void layoutImageElement(Box box, TextAreaModel.ImageElement ie) {
        Image image = selectImage(ie.getImageName());
        if(image == null) {
            return;
        }

        LImage li = new LImage(ie, image);
        layout(box, ie, li);
    }

    private void layoutWidgetElement(Box box, TextAreaModel.WidgetElement we) {
        Widget widget = widgets.get(we.getWidgetName());
        if(widget == null) {
            WidgetResolver resolver = widgetResolvers.get(we.getWidgetName());
            if(resolver != null) {
                widget = resolver.resolveWidget(we.getWidgetName(), we.getWidgetParam());
            }
            if(widget == null) {
                return;
            }
        }

        if(widget.getParent() != null) {
            Logger.getLogger(TextArea.class.getName()).log(Level.SEVERE, "Widget already added: " + widget);
            return;
        }

        super.insertChild(widget, getNumChildren());
        widget.adjustSize();
        
        LWidget lw = new LWidget(we, widget);
        lw.width = widget.getWidth();
        lw.height = widget.getHeight();

        layout(box, we, lw);
    }

    private void layout(Box box, TextAreaModel.Element e, LBox le) {
        Style style = e.getStyle();

        FloatPosition floatPosition = style.get(StyleAttribute.FLOAT_POSITION, styleClassResolver);
        TextAreaModel.Display display = style.get(StyleAttribute.DISPLAY, styleClassResolver);

        le.marginTop = (short)convertToPX0(style, StyleAttribute.MARGIN_TOP, box.boxWidth);
        le.marginLeft = (short)convertToPX0(style, StyleAttribute.MARGIN_LEFT, box.boxWidth);
        le.marginRight = (short)convertToPX0(style, StyleAttribute.MARGIN_RIGHT, box.boxWidth);

        int height = convertToPX(style, StyleAttribute.HEIGHT, le.height);
        if(height > 0) {
            le.height = height;
        }

        layout(box, e, le, floatPosition, display);
    }
    
    private void layout(Box box, TextAreaModel.Element e, LBox le, FloatPosition floatPos, TextAreaModel.Display display) {
        boolean leftRight = floatPos != FloatPosition.NONE;

        if(leftRight || display != TextAreaModel.Display.INLINE) {
            box.nextLine(false);
        }

        Style style = e.getStyle();
        int width = convertToPX(style, StyleAttribute.WIDTH, box.boxWidth);
        if(width > 0) {
            le.width = width;
        }

        box.advancePastFloaters(width, le.marginLeft, le.marginRight);
        if(le.width > box.lineWidth) {
            le.width = box.lineWidth;
        }

        if(leftRight) {
            if(floatPos == FloatPosition.RIGHT) {
                le.x = box.computeRightPadding(le.marginRight) - le.width;
                box.objRight.add(le);
            } else {
                le.x = box.computeLeftPadding(le.marginLeft);
                box.objLeft.add(le);
            }
        } else if(display == TextAreaModel.Display.INLINE) {
            if(box.getRemaining() < le.width && !box.isAtStartOfLine()) {
                box.nextLine(false);
            }
            le.x = box.getXAndAdvance(le.width);
        } else {
            switch(style.get(StyleAttribute.HORIZONTAL_ALIGNMENT, styleClassResolver)) {
            case CENTER:
            case JUSTIFY:
                le.x = box.lineStartX + (box.lineWidth - le.width) / 2;
                break;

            case RIGHT:
                le.x = box.computeRightPadding(le.marginRight) - le.width;
                break;

            default:
                le.x = box.computeLeftPadding(le.marginLeft);
            }
        }

        box.layout.add(le);

        if(leftRight) {
            assert box.lineStartIdx == box.layout.size() - 1;
            box.lineStartIdx++;
            le.y = box.computeTopPadding(le.marginTop);
            box.computePadding();
        } else {
            if(display != TextAreaModel.Display.INLINE) {
                box.nextLine(false);
            }
            box.setMarginBottom(convertToPX0(style, StyleAttribute.MARGIN_BOTTOM, box.boxWidth));
        }
    }

    int convertToPX(Style style, StyleAttribute<Value> attribute, int full) {
        style = style.resolve(attribute, styleClassResolver);
        Value valueUnit = style.getNoResolve(attribute, styleClassResolver);
        
        Font font = null;
        if(valueUnit.unit.isFontBased()) {
            font = selectFont(style);
            if(font == null) {
                return 0;
            }
        }
        
        float value = valueUnit.value;
        switch(valueUnit.unit) {
            case EM:
                value *= font.getEM();
                break;
            case EX:
                value *= font.getEX();
                break;
            case PERCENT:
                value *= full * 0.01f;
                break;
        }
        if(value >= Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if(value <= Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return Math.round(value);
    }

    int convertToPX0(Style style, StyleAttribute<Value> attribute, int full) {
        return Math.max(0, convertToPX(style, attribute, full));
    }

    private Font selectFont(Style style) {
        String fontName = style.get(StyleAttribute.FONT_NAME, styleClassResolver);
        if(fontName != null && fonts != null) {
            Font font = fonts.getFont(fontName);
            if(font != null) {
                return font;
            }
        }
        return defaultFont;
    }

    private Image selectImage(Style style, StyleAttribute<String> element) {
        String imageName = style.get(element, styleClassResolver);
        if(imageName != null) {
            return selectImage(imageName);
        } else {
            return null;
        }
    }

    private Image selectImage(String name) {
        Image image = userImages.get(name);
        if(image != null) {
            return image;
        }
        for(int i=0 ; i<imageResolvers.size() ; i++) {
            image = imageResolvers.get(i).resolveImage(name);
            if(image != null) {
                return image;
            }
        }
        if(images != null) {
            return images.getImage(name);
        }
        return null;
    }

    private void layoutTextElement(Box box, TextAreaModel.TextElement te) {
        final String text = te.getText();
        final Style style = te.getStyle();
        final Font font = selectFont(style);
        final boolean pre = style.get(StyleAttribute.PREFORMATTED, styleClassResolver);

        if(font == null) {
            return;
        }

        box.setupTextParams(te, font);

        int idx = 0;
        while(idx < text.length()) {
            int end = TextUtil.indexOf(text, '\n', idx);
            if(pre) {
                layoutTextPre(box, te, font, text, idx, end);
            } else {
                layoutText(box, te, font, text, idx, end);
            }
            
            if(end < text.length() && text.charAt(end) == '\n') {
                end++;
                box.nextLine(true);
            }
            idx = end;
        }

        box.resetTextParams(te.isParagraphEnd());
        box.setMarginBottom(convertToPX0(style, StyleAttribute.MARGIN_BOTTOM, box.boxWidth));
    }

    private void layoutText(Box box, TextAreaModel.TextElement te, Font font,
            String text, int textStart, int textEnd) {
        int idx = textStart;
        // trim start
        while(textStart < textEnd && isSkip(text.charAt(textStart))) {
            textStart++;
        }
        // trim end
        boolean endsWithSpace = false;
        while(textEnd > textStart && isSkip(text.charAt(textEnd-1))) {
            endsWithSpace = true;
            textEnd--;
        }

        // check if we skipped white spaces and the previous element in this
        // row was not a text cell
        if(textStart > idx && box.prevOnLineEndsNotWithSpace()) {
            box.curX += font.getSpaceWidth();
        }

        idx = textStart;
        while(idx < textEnd) {
            assert !isSkip(text.charAt(idx));

            int end = idx;
            if(box.textAlignment != TextAreaModel.HAlignment.JUSTIFY) {
                end = idx + font.computeVisibleGlpyhs(text, idx, textEnd, box.getRemaining());
                if(end < textEnd) {
                    // if we are at a punctuation then walk backwards until we hit
                    // the word or a break. This ensures that the punctuation stays
                    // at the end of a word
                    while(end > idx && isPunctuation(text.charAt(end))) {
                        end--;
                    }

                    // if we are not at the end of this text element
                    // and the next character is not a space
                    if(!isBreak(text.charAt(end))) {
                        // then we walk backwards until we find spaces
                        // this prevents the line ending in the middle of a word
                        while(end > idx && !isBreak(text.charAt(end-1))) {
                            end--;
                        }
                    }
                }

                // now walks backwards until we hit the end of the previous word
                while(end > idx && isSkip(text.charAt(end-1))) {
                    end--;
                }
            }

            boolean advancePastFloaters = false;

            // if we found no word that fits
            if(end == idx) {
                // we may need a new line
                if(box.textAlignment != TextAreaModel.HAlignment.JUSTIFY && box.nextLine(false)) {
                    continue;
                }
                // or we already are at the start of a line
                // just put the word there even if it doesn't fit
                while(end < textEnd && !isBreak(text.charAt(end))) {
                    end++;
                }
                // some characters need to stay at the end of a word
                while(end < textEnd && isPunctuation(text.charAt(end))) {
                    end++;
                }
                advancePastFloaters = true;
            }

            if(idx < end) {
                LText lt = new LText(te, font, text, idx, end);
                if(advancePastFloaters) {
                    box.advancePastFloaters(lt.width, 0, 0);
                }
                if(box.textAlignment == TextAreaModel.HAlignment.JUSTIFY && box.getRemaining() < lt.width) {
                    box.nextLine(false);
                }

                int width = lt.width;
                if(end < textEnd && isSkip(text.charAt(end))) {
                    width += font.getSpaceWidth();
                }

                lt.x = box.getXAndAdvance(width);
                lt.marginTop = (short)box.marginTop;
                box.layout.add(lt);
            }

            // find the start of the next word
            idx = end;
            while(idx < textEnd && isSkip(text.charAt(idx))) {
                idx++;
            }
        }

        if(!box.isAtStartOfLine() && endsWithSpace) {
            box.curX += font.getSpaceWidth();
        }
    }

    private void layoutTextPre(Box box, TextAreaModel.TextElement te, Font font,
            String text, int textStart, int textEnd) {
        int idx = textStart;
        while(idx < textEnd) {
            box.nextLine(false);

            while(idx < textEnd) {
                if(text.charAt(idx) == '\t') {
                    idx++;
                    int tabX = box.computeNextTabStop(font);
                    if(tabX < box.lineWidth) {
                        box.curX = tabX;
                    } else if(!box.isAtStartOfLine()) {
                        break;
                    }
                }

                int tabIdx = text.indexOf('\t', idx);
                int end = textEnd;
                if(tabIdx >= 0 && tabIdx < textEnd) {
                    end = tabIdx;
                }

                if(end > idx) {
                    int count = font.computeVisibleGlpyhs(text, idx, end, box.getRemaining());
                    if(count == 0 && !box.isAtStartOfLine()) {
                        break;
                    }

                    end = idx + Math.max(1, count);

                    LText lt = new LText(te, font, text, idx, end);
                    lt.x = box.getXAndAdvance(lt.width);
                    lt.marginTop = (short)box.marginTop;
                    box.layout.add(lt);
                }

                idx = end;
            }
        }
        box.nextLine(false);
    }

    private void layoutListElement(Box box, TextAreaModel.ListElement le) {
        Style style = le.getStyle();
        Image image = selectImage(style, StyleAttribute.LIST_STYLE_IMAGE);
        if(image != null) {
            int marginTop = convertToPX0(style, StyleAttribute.MARGIN_TOP, box.boxWidth);
            box.curY = box.computeTopPadding(marginTop);
            box.checkFloaters();
            
            LImage li = new LImage(le, image);
            layout(box, le, li, TextAreaModel.FloatPosition.LEFT, TextAreaModel.Display.BLOCK);
            
            int imageHeight = li.height;
            li.height = Short.MAX_VALUE;

            layoutElements(box, le);
            box.nextLine(false);

            li.height = imageHeight;

            box.objLeft.remove(li);
            box.computePadding();
        } else {
            layoutElements(box, le);
            box.nextLine(false);
        }
    }

    private void layoutBlockElement(Box box, TextAreaModel.BlockElement be) {
        box.nextLine(false);

        final Style style = be.getStyle();
        final FloatPosition floatPosition = style.get(StyleAttribute.FLOAT_POSITION, styleClassResolver);

        LImage bgImage = null;
        Image image = selectImage(style, StyleAttribute.BACKGROUND_IMAGE);
        if(image != null) {
            bgImage = new LImage(be, image);
            box.clip.bgImages.add(bgImage);
        }

        int marginTop = convertToPX0(style, StyleAttribute.MARGIN_TOP, box.boxWidth);
        int marginLeft = convertToPX0(style, StyleAttribute.MARGIN_LEFT, box.boxWidth);
        int marginRight = convertToPX0(style, StyleAttribute.MARGIN_RIGHT, box.boxWidth);
        int marginBottom = convertToPX0(style, StyleAttribute.MARGIN_BOTTOM, box.boxWidth);
        int paddingTop = convertToPX0(style, StyleAttribute.PADDING_TOP, box.boxWidth);
        int paddingBottom = convertToPX0(style, StyleAttribute.PADDING_BOTTOM, box.boxWidth);

        int bgX = box.computeLeftPadding(marginLeft);
        int bgY = box.computeTopPadding(marginTop);
        int bgWidth;
        int bgHeight;

        int remaining = Math.max(0, box.computeRightPadding(marginRight) - bgX);

        if(floatPosition == TextAreaModel.FloatPosition.NONE) {
            bgWidth = remaining;
        } else {
            bgWidth = convertToPX(style, StyleAttribute.WIDTH, box.boxWidth);
        }

        int paddingLeft = convertToPX0(style, StyleAttribute.PADDING_LEFT, bgWidth);
        int paddingRight = convertToPX0(style, StyleAttribute.PADDING_RIGHT, bgWidth);

        bgWidth += paddingLeft + paddingRight;

        if(floatPosition != TextAreaModel.FloatPosition.NONE) {
            box.advancePastFloaters(bgWidth, marginLeft, marginRight);
            
            bgX = box.computeLeftPadding(marginLeft);
            bgY = Math.max(bgY, box.curY);
            remaining = Math.max(0, box.computeRightPadding(marginRight) - bgX);
        }

        bgWidth = Math.max(0, Math.min(bgWidth, remaining));

        if(floatPosition == TextAreaModel.FloatPosition.RIGHT) {
            bgX = box.computeRightPadding(marginRight) - bgWidth;
        }

        LClip clip = new LClip(be);
        box.layout.add(clip);
        
        Box blockBox = new Box(clip, paddingTop, paddingLeft,
                Math.max(0, bgWidth - paddingLeft - paddingRight));
        layoutElements(blockBox, be);
        blockBox.nextLine(false);
        blockBox.clearFloater(TextAreaModel.Clear.BOTH);
        bgHeight = blockBox.curY + paddingBottom;
        bgHeight = Math.max(bgHeight, convertToPX(style, StyleAttribute.HEIGHT, bgHeight));
        marginBottom = Math.max(marginBottom, blockBox.marginBottomAbs - blockBox.curY);

        clip.x = bgX;
        clip.y = bgY;
        clip.width = bgWidth;
        clip.height = bgHeight;
        
        // sync main box with layout
        box.lineStartIdx = box.layout.size();

        if(floatPosition == TextAreaModel.FloatPosition.NONE) {
            box.curY = bgY + bgHeight;
            box.setMarginBottom(marginBottom);
        } else {
            LBox dummy = new LBox(be);
            dummy.marginLeft = (short)marginLeft;
            dummy.marginRight = (short)marginRight;
            dummy.x = bgX;
            dummy.y = bgY;
            dummy.width = bgWidth;
            dummy.height = bgHeight + marginBottom;

            if(floatPosition == TextAreaModel.FloatPosition.RIGHT) {
                box.objRight.add(dummy);
            } else {
                box.objLeft.add(dummy);
            }
            box.computePadding();
        }
        
        if(bgImage != null) {
            bgImage.x = bgX;
            bgImage.y = bgY;
            bgImage.width = bgWidth;
            bgImage.height = bgHeight;
        }
    }

    private boolean isSkip(char ch) {
        return Character.isWhitespace(ch);
    }

    private boolean isPunctuation(char ch) {
        return ":;,.-!?".indexOf(ch) >= 0;
    }

    private boolean isBreak(char ch) {
        return Character.isWhitespace(ch) || isPunctuation(ch);
    }

    class Box {
        final LClip clip;
        final ArrayList<LElement> layout;
        final ArrayList<LBox> objLeft = new ArrayList<LBox>();
        final ArrayList<LBox> objRight = new ArrayList<LBox>();
        final int boxLeft;
        final int boxWidth;
        int curY;
        int curX;
        int lineStartIdx;
        int paddingLeft;
        int paddingRight;
        int marginTop;
        int marginLeft;
        int marginRight;
        int marginBottomAbs;
        int lineStartX;
        int lineWidth;
        int fontLineHeight;
        boolean inParagraph;
        boolean wasAutoBreak;
        TextAreaModel.HAlignment textAlignment;

        public Box(LClip clip, int boxTop, int boxLeft, int boxWidth) {
            this.clip = clip;
            this.layout = clip.layout;
            this.boxLeft = boxLeft;
            this.boxWidth = boxWidth;
            this.curX = boxLeft;
            this.curY = boxTop;
            this.lineStartIdx = layout.size();
            this.lineStartX = boxLeft;
            this.lineWidth = boxWidth;
            this.textAlignment = TextAreaModel.HAlignment.LEFT;
        }

        void computePadding() {
            int left = computeLeftPadding(marginLeft);
            int right = computeRightPadding(marginRight);

            left += paddingLeft;
            right -= paddingRight;

            lineStartX = left;
            lineWidth = Math.max(0, right - left);

            if(isAtStartOfLine()) {
                curX = lineStartX;
            }
        }

        int computeLeftPadding(int marginLeft) {
            int left = boxLeft + marginLeft;

            for(int i=0,n=objLeft.size() ; i<n ; i++) {
                LBox e = objLeft.get(i);
                left = Math.max(left, e.x + e.width + Math.max(e.marginRight, marginLeft));
            }

            return left;
        }

        int computeRightPadding(int marginRight) {
            int right = boxLeft + boxWidth - marginRight;

            for(int i=0,n=objRight.size() ; i<n ; i++) {
                LBox e = objRight.get(i);
                right = Math.min(right, e.x - Math.max(e.marginLeft, marginRight));
            }

            return right;
        }

        int computePaddingWidth(int marginLeft, int marginRight) {
            return Math.max(0, computeRightPadding(marginRight) - computeLeftPadding(marginLeft));
        }

        int computeTopPadding(int marginTop) {
            return Math.max(marginBottomAbs, curY + marginTop);
        }

        void setMarginBottom(int marginBottom) {
            marginBottomAbs = Math.max(marginBottomAbs, curY + marginBottom);
        }
        
        int getRemaining() {
            return Math.max(0, lineWidth - curX + lineStartX);
        }

        int getXAndAdvance(int amount) {
            int x = curX;
            curX = x + amount;
            return x;
        }

        boolean isAtStartOfLine() {
            return lineStartIdx == layout.size();
        }

        boolean prevOnLineEndsNotWithSpace() {
            int layoutSize = layout.size();
            if(lineStartIdx < layoutSize) {
                LElement le = layout.get(layoutSize-1);
                if(le instanceof LText) {
                    LText lt = (LText)le;
                    return !isSkip(lt.text.charAt(lt.end-1));
                }
                return true;
            }
            return false;
        }

        void checkFloaters() {
            removeObjFromList(objLeft);
            removeObjFromList(objRight);
            computePadding();
            // curX is set by computePadding()
        }

        void clearFloater(TextAreaModel.Clear clear) {
            if(clear != TextAreaModel.Clear.NONE) {
                int targetY = -1;
                if(clear == TextAreaModel.Clear.LEFT || clear == TextAreaModel.Clear.BOTH) {
                    for(int i=0,n=objLeft.size() ; i<n ; ++i) {
                        LBox le = objLeft.get(i);
                        if(le.height != Short.MAX_VALUE) {  // special case for list elements
                            targetY = Math.max(targetY, le.y + le.height);
                        }
                    }
                }
                if(clear == TextAreaModel.Clear.RIGHT || clear == TextAreaModel.Clear.BOTH) {
                    for(int i=0,n=objRight.size() ; i<n ; ++i) {
                        LBox le = objRight.get(i);
                        targetY = Math.max(targetY, le.y + le.height);
                    }
                }
                if(targetY >= 0) {
                    nextLine(false);
                    if(targetY > curY) {
                        curY = targetY;
                        checkFloaters();
                    }
                }
            }
        }

        void advancePastFloaters(int requiredWidth, int marginLeft, int marginRight) {
            if(computePaddingWidth(marginLeft, marginRight) < requiredWidth) {
                nextLine(false);
                do {
                    int targetY = Integer.MAX_VALUE;
                    if(!objLeft.isEmpty()) {
                        LBox le = objLeft.get(objLeft.size()-1);
                        if(le.height != Short.MAX_VALUE) {  // special case for list elements
                            targetY = Math.min(targetY, le.y + le.height);
                        }
                    }
                    if(!objRight.isEmpty()) {
                        LBox le = objRight.get(objRight.size()-1);
                        targetY = Math.min(targetY, le.y + le.height);
                    }
                    if(targetY == Integer.MAX_VALUE || targetY < curY) {
                        return;
                    }
                    curY = targetY;
                    checkFloaters();
                } while(computePaddingWidth(marginLeft, marginRight) < requiredWidth);
            }
        }

        boolean nextLine(boolean force) {
            if(isAtStartOfLine()) {
                if(!wasAutoBreak && force) {
                    curY += fontLineHeight;
                    checkFloaters();
                    wasAutoBreak = false;
                    marginTop = 0;
                    return true;
                }
                return false;
            }
            wasAutoBreak = !force;
            marginTop = 0;
            
            int lineHeight = 0;
            for(int idx=lineStartIdx ; idx<layout.size() ; idx++) {
                LElement le = layout.get(idx);
                lineHeight = Math.max(lineHeight, le.height);
            }

            LElement lastElement = layout.get(layout.size() - 1);
            int remaining = (lineStartX + lineWidth) - (lastElement.x + lastElement.width);

            switch(textAlignment) {
            case RIGHT: {
                for(int idx=lineStartIdx ; idx<layout.size() ; idx++) {
                    LElement le = layout.get(idx);
                    le.x += remaining;
                }
                break;
            }
            case CENTER: {
                int offset = remaining / 2;
                for(int idx=lineStartIdx ; idx<layout.size() ; idx++) {
                    LElement le = layout.get(idx);
                    le.x += offset;
                }
                break;
            }
            case JUSTIFY:
                if(remaining < lineWidth / 4) {
                    int num = layout.size() - lineStartIdx;
                    for(int i=1 ; i<num ; i++) {
                        LElement le = layout.get(lineStartIdx + i);
                        int offset = remaining * i / (num-1);
                        le.x += offset;
                    }
                }
                break;
            }

            int targetY = curY;

            for(int idx=lineStartIdx ; idx<layout.size() ; idx++) {
                LElement le = layout.get(idx);
                targetY = Math.max(targetY, computeTopPadding(le.marginTop));
                switch(le.element.getStyle().get(StyleAttribute.VERTICAL_ALIGNMENT, styleClassResolver)) {
                case BOTTOM:
                    le.y = lineHeight - le.height;
                    break;
                case TOP:
                    le.y = 0;
                    break;
                case MIDDLE:
                    le.y = (lineHeight - le.height)/2;
                    break;
                case FILL:
                    le.y = 0;
                    le.height = lineHeight;
                    break;
                }
            }

            for(int idx=lineStartIdx ; idx<layout.size() ; idx++) {
                LElement le = layout.get(idx);
                le.y += targetY;
            }

            lineStartIdx = layout.size();
            curY = targetY + lineHeight;
            checkFloaters();
            // curX is set by computePadding() inside checkFloaters()
            return true;
        }

        int computeNextTabStop(Font font) {
            int x = curX - lineStartX + font.getSpaceWidth();
            int tabSize = 8 * font.getEM();
            return curX + tabSize - (x % tabSize);
        }

        private void removeObjFromList(ArrayList<LBox> list) {
            for(int i=list.size() ; i-->0 ;) {
                LBox e = list.get(i);
                if(e.y + e.height <= curY) {
                    // can't update marginBottomAbs here - results in layout error for text
                    list.remove(i);
                }
            }
        }

        void resetTextParams(boolean endParagraph) {
            if(endParagraph) {
                if(textAlignment == TextAreaModel.HAlignment.JUSTIFY) {
                    textAlignment = TextAreaModel.HAlignment.LEFT;
                }
                nextLine(false);
                inParagraph = false;
            }
            
            if(!inParagraph) {
                paddingLeft = 0;
                paddingRight = 0;
                textAlignment = TextAreaModel.HAlignment.LEFT;
                computePadding();
            }
        }

        void setupTextParams(TextElement te, Font font) {
            fontLineHeight = font.getLineHeight();

            if(te.isParagraphStart()) {
                nextLine(false);
                inParagraph = true;
            }

            Style style = te.getStyle();

            if(te.isParagraphStart() || (!inParagraph && isAtStartOfLine())) {
                marginLeft = convertToPX0(style, StyleAttribute.MARGIN_LEFT, boxWidth);
                marginRight = convertToPX0(style, StyleAttribute.MARGIN_RIGHT, boxWidth);
                paddingLeft = convertToPX0(style, StyleAttribute.PADDING_LEFT, boxWidth);
                paddingRight = convertToPX0(style, StyleAttribute.PADDING_RIGHT, boxWidth);
                textAlignment = style.get(StyleAttribute.HORIZONTAL_ALIGNMENT, styleClassResolver);
                computePadding();
                curX = Math.max(0, lineStartX + convertToPX(style, StyleAttribute.TEXT_IDENT, boxWidth));
            }

            marginTop = convertToPX0(style, StyleAttribute.MARGIN_TOP, boxWidth);
        }
    }

    static class LElement {
        final TextAreaModel.Element element;
        int x;
        int y;
        int width;
        int height;
        short marginTop;

        public LElement(TextAreaModel.Element element) {
            this.element = element;
        }

        void adjustWidget(int offX, int offY) {}
        void draw(int offX, int offY, AnimationState as) {}
        void destroy() {}

        boolean isInside(int x, int y) {
            return (x >= this.x) && (x < this.x + this.width) &&
                    (y >= this.y) && (y < this.y + this.height);
        }
        LElement find(int x, int y) {
            return this;
        }
    }

    static class LText extends LElement {
        final Font font;
        final String text;
        final int start;
        final int end;
        FontCache cache;

        public LText(TextAreaModel.Element element, Font font, String text, int start, int end) {
            super(element);
            this.font = font;
            this.text = text;
            this.start = start;
            this.end = end;
            this.cache = font.cacheText(null, text, start, end);
            this.height = font.getLineHeight();

            if(cache != null) {
                this.width = cache.getWidth();
            } else {
                this.width = font.computeTextWidth(text, start, end);
            }
        }

        @Override
        void draw(int offX, int offY, AnimationState as) {
            if(cache != null) {
                cache.draw(as, x+offX, y+offY);
            } else {
                font.drawText(as, x+offX, y+offY, text, start, end);
            }
        }

        @Override
        void destroy() {
            if(cache != null) {
                cache.destroy();
                cache = null;
            }
        }
    }

    static class LWidget extends LBox {
        final Widget widget;

        public LWidget(TextAreaModel.Element element, Widget widget) {
            super(element);
            this.widget = widget;
        }

        @Override
        void adjustWidget(int offX, int offY) {
            widget.setPosition(x + offX, y + offY);
            widget.setSize(width, height);
        }
    }

    static class LBox extends LElement {
        short marginLeft;
        short marginRight;
        short marginBottom;

        public LBox(Element element) {
            super(element);
        }
    }

    static class LImage extends LBox {
        final Image img;

        public LImage(Element element, Image img) {
            super(element);
            this.img = img;
            this.width = img.getWidth();
            this.height = img.getHeight();
        }
        
        @Override
        void draw(int offX, int offY, AnimationState as) {
            img.draw(as, x+offX, y+offY, width, height);
        }
    }

    class LClip extends LBox {
        final ArrayList<LElement> layout;
        final ArrayList<LImage> bgImages;

        public LClip(Element element) {
            super(element);
            this.layout = new ArrayList<LElement>();
            this.bgImages = new ArrayList<LImage>();
        }

        @Override
        void draw(int offX, int offY, AnimationState as) {
            offX += x;
            offY += y;
            GUI gui = getGUI();
            gui.clipEnter(offX, offY, width, height);
            try {
                drawNoClip(offX, offY, as);
            } finally {
                gui.clipLeave();
            }
        }

        void drawNoClip(int offX, int offY, AnimationState as) {
            final ArrayList<LElement> ll = layout;
            final TextAreaModel.Element hoverElement;
            if(curLElementUnderMouse != null) {
                hoverElement = curLElementUnderMouse.element;
            } else {
                hoverElement = null;
            }
            for(int i=0,n=ll.size() ; i<n ; i++) {
                LElement le = ll.get(i);
                as.setAnimationState(STATE_HOVER, hoverElement == le.element);
                le.draw(offX, offY, as);
            }
        }

        @Override
        void adjustWidget(int offX, int offY) {
            offX += x;
            offY += y;
            for(int i=0,n=layout.size() ; i<n ; i++) {
                layout.get(i).adjustWidget(offX, offY);
            }
            offX -= getInnerX();
            offY -= getInnerY();
            for(int i=0,n=bgImages.size() ; i<n ; i++) {
                LImage img = bgImages.get(i);
                img.x += offX;
                img.y += offY;
                allBGImages.add(img);
            }
        }

        @Override
        void destroy() {
            for(int i=0,n=layout.size() ; i<n ; i++) {
                layout.get(i).destroy();
            }
            layout.clear();
            bgImages.clear();
        }

        @Override
        LElement find(int x, int y) {
            x -= this.x;
            y -= this.y;
            for(LElement le : layout) {
                if(le.isInside(x, y)) {
                    return le.find(x, y);
                }
            }
            return null;
        }
    }

    private static class StyleClassResolverImpl implements StyleClassResolver {
        private final ParameterMap classes;
        private final HashMap<String, Style> cache;

        public StyleClassResolverImpl(ParameterMap classes) {
            this.classes = classes;
            this.cache = new HashMap<String, Style>();
        }

        public Style resolve(String classRef) {
            Style style = cache.get(classRef);
            if(style == null) {
                String css = classes.getParameter(classRef, "");
                style = new CSSStyle(css);
                cache.put(classRef, style);
            }
            return style;
        }
    }
}
