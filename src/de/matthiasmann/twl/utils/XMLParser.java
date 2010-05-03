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
package de.matthiasmann.twl.utils;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * A helper class to make parsing of XML files easier
 *
 * It will also warn if a XML tag contains attributes which are not
 * consumed.
 *
 * @author Matthias Mann
 */
public class XMLParser implements Closeable {

    private static final Class<?>[] XPP_CLASS = {XmlPullParser.class};
    
    private final XmlPullParser xpp;
    private final String source;
    private final InputStream inputStream;
    private final BitSet unusedAttributes = new BitSet();
    private String loggerName = XMLParser.class.getName();

    public XMLParser(XmlPullParser xpp, String source) {
        if(xpp == null) {
            throw new NullPointerException("xpp");
        }
        this.xpp = xpp;
        this.source = source;
        this.inputStream = null;
    }

    /**
     * Creates a XMLParser for the given URL.
     *
     * This method also calls {@code URL.getContent} which allows a custom
     * URLStreamHandler to return a class implementing {@code XmlPullParser}.
     *
     * @param url the URL to parse
     * @throws XmlPullParserException if the resource is not a valid XML file
     * @throws IOException if the resource could not be read
     * @see URLStreamHandler
     * @see URL#getContent(java.lang.Class[])
     * @see org.xmlpull.v1.XmlPullParser
     */
    public XMLParser(URL url) throws XmlPullParserException, IOException {
        if(url == null) {
            throw new NullPointerException("url");
        }
        
        XmlPullParser xpp_ = null;
        InputStream is = null;

        this.source = url.toString();
        
        try {
            xpp_ = (XmlPullParser)url.getContent(XPP_CLASS);
        } catch (IOException ex) {
            // ignore
        }

        if(xpp_ == null) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            xpp_ = factory.newPullParser();
            is = url.openStream();
            if(is == null) {
                throw new FileNotFoundException(source);
            }
            xpp_.setInput(is, "UTF8");
        }

        this.xpp = xpp_;
        this.inputStream = is;
    }

    public void close() throws IOException {
        if(inputStream != null) {
            inputStream.close();
        }
    }

    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    /**
     * @see XmlPullParser#next() 
     */
    public int next() throws XmlPullParserException, IOException {
        warnUnusedAttributes();
        int type = xpp.next();
        handleType(type);
        return type;
    }

    /**
     * @see XmlPullParser#nextTag() 
     */
    public int nextTag() throws XmlPullParserException, IOException {
        warnUnusedAttributes();
        int type = xpp.nextTag();
        handleType(type);
        return type;
    }

    /**
     * @see XmlPullParser#nextText()
     */
    public String nextText() throws XmlPullParserException, IOException {
        warnUnusedAttributes();
        return xpp.nextText();
    }

    public boolean isStartTag() throws XmlPullParserException {
        return xpp.getEventType() == XmlPullParser.START_TAG;
    }

    public boolean isEndTag() throws XmlPullParserException {
        return xpp.getEventType() == XmlPullParser.END_TAG;
    }

    public String getPositionDescription() {
        String desc = xpp.getPositionDescription();
        if(source != null) {
            return desc + " in " + source;
        }
        return desc;
    }

    public String getName() {
        return xpp.getName();
    }

    /**
     * @see XmlPullParser#require(int, java.lang.String, java.lang.String) 
     */
    public void require(int type, String namespace, String name) throws XmlPullParserException, IOException {
        xpp.require(type, namespace, name);
    }

    public String getAttributeValue(int index) {
        unusedAttributes.clear(index);
        return xpp.getAttributeValue(index);
    }

    public String getAttributeNamespace(int index) {
        return xpp.getAttributeNamespace(index);
    }

    public String getAttributeName(int index) {
        return xpp.getAttributeName(index);
    }

    public int getAttributeCount() {
        return xpp.getAttributeCount();
    }

    public String getAttributeValue(String namespace, String name) {
        for(int i=0,n=xpp.getAttributeCount() ; i<n ; i++) {
            if((namespace == null || namespace.equals(xpp.getAttributeNamespace(i))) &&
                    name.equals(xpp.getAttributeName(i))) {
                return getAttributeValue(i);
            }
        }
        return null;
    }


    public String getAttributeNotNull(String attribute) throws XmlPullParserException {
        String value = getAttributeValue(null, attribute);
        if(value == null) {
            missingAttribute(attribute);
        }
        return value;
    }

    public boolean parseBoolFromAttribute(String attribName) throws XmlPullParserException {
        return parseBool(getAttributeNotNull(attribName));
    }

    public boolean parseBoolFromText() throws XmlPullParserException, IOException {
        return parseBool(nextText());
    }

    public boolean parseBoolFromAttribute(String attribName, boolean defaultValue) throws XmlPullParserException {
        String value = getAttributeValue(null, attribName);
        if(value == null) {
            return defaultValue;
        }
        return parseBool(value);
    }

    public int parseIntFromAttribute(String attribName) throws XmlPullParserException {
        return parseInt(getAttributeNotNull(attribName));
    }

    public int parseIntFromAttribute(String attribName, int defaultValue) throws XmlPullParserException {
        String value = getAttributeValue(null, attribName);
        if(value == null) {
            return defaultValue;
        }
        return parseInt(value);
    }

    public <E extends Enum<E>> E parseEnumFromAttribute(String attribName, Class<E> enumClazz) throws XmlPullParserException {
        return parseEnum(enumClazz, getAttributeNotNull(attribName));
    }

    public <E extends Enum<E>> E parseEnumFromAttribute(String attribName, Class<E> enumClazz, E defaultValue) throws XmlPullParserException {
        String value = getAttributeValue(null, attribName);
        if(value == null) {
            return defaultValue;
        }
        return parseEnum(enumClazz, value);
    }
    
    public <E extends Enum<E>> E parseEnumFromText(Class<E> enumClazz) throws XmlPullParserException, IOException {
        return parseEnum(enumClazz, nextText());
    }

    public Map<String, String> getUnusedAttributes() {
        if(unusedAttributes.isEmpty()) {
            return Collections.<String, String>emptyMap();
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        for(int i=-1 ; (i=unusedAttributes.nextSetBit(i+1))>=0 ;) {
            result.put(xpp.getAttributeName(i), xpp.getAttributeValue(i));
        }
        unusedAttributes.clear();
        return result;
    }

    public void ignoreOtherAttributes() {
        unusedAttributes.clear();
    }
    
    public XmlPullParserException error(String msg) {
        return new XmlPullParserException(msg, xpp, null);
    }
    
    public XmlPullParserException error(String msg, Throwable cause) {
        return (XmlPullParserException)new XmlPullParserException(msg, xpp, cause).initCause(cause);
    }

    public XmlPullParserException unexpected() {
        return new XmlPullParserException("Unexpected '"+xpp.getName()+"'", xpp, null);
    }

    protected <E extends Enum<E>> E parseEnum(Class<E> enumClazz, String value) throws XmlPullParserException {
        try {
            return Enum.valueOf(enumClazz, value.toUpperCase());
        } catch (IllegalArgumentException unused) {
        }
        try {
            return Enum.valueOf(enumClazz, value);
        } catch (IllegalArgumentException unused) {
        }
        throw new XmlPullParserException("Unknown enum value \"" + value
                + "\" for enum class " + enumClazz, xpp, null);
    }

    protected boolean parseBool(String value) throws XmlPullParserException {
        if("true".equals(value)) {
            return true;
        } else if("false".equals(value)) {
            return false;
        } else {
            throw new XmlPullParserException("boolean value must be 'true' or 'false'", xpp, null);
        }
    }

    protected int parseInt(String value) throws XmlPullParserException {
        try {
            return Integer.parseInt(value);
        } catch(NumberFormatException ex) {
            throw (XmlPullParserException)(new XmlPullParserException(
                    "Unable to parse integer", xpp, ex).initCause(ex));
        }
    }

    protected void missingAttribute(String attribute) throws XmlPullParserException {
        throw new XmlPullParserException("missing '" + attribute + "' on '" + xpp.getName() + "'", xpp, null);
    }

    protected void handleType(int type) {
        unusedAttributes.clear();
        switch (type) {
            case XmlPullParser.START_TAG:
                unusedAttributes.set(0, xpp.getAttributeCount());
                break;
        }
    }

    protected void warnUnusedAttributes() {
        if(!unusedAttributes.isEmpty()) {
            for(int i=-1 ; (i=unusedAttributes.nextSetBit(i+1))>=0 ;) {
                getLogger().log(Level.WARNING, "Unused attribute '" + xpp.getAttributeName(i) + "' on '" +
                        xpp.getName() + "' at " + getPositionDescription());
            }
        }
    }

    protected Logger getLogger() {
        return Logger.getLogger(loggerName);
    }

}
