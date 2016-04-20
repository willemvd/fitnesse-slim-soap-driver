package com.xebia.fitnesse.slim.soap;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map.Entry;

public class SoapFixture {

    private Transformer transformer;

    private MessageFactory messageFactory;

    private SOAPMessage requestMessage;

    private SOAPMessage responseMessage;

    private SimpleNamespaceContext nsContext;

    private SOAPConnection soapConnection;

    private SOAPXPathHelper xmlHelper;

    public SoapFixture() throws SOAPException, TransformerConfigurationException, TransformerFactoryConfigurationError {
        nsContext = new SimpleNamespaceContext();
        xmlHelper = new SOAPXPathHelper(nsContext);
        messageFactory = MessageFactory.newInstance();
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        soapConnection = SOAPConnectionFactory.newInstance().createConnection();
        prepareForNewRequestMessage();
    }

    private void prepareForNewRequestMessage() throws SOAPException {
        SOAPMessage newRequestMessage = messageFactory.createMessage();
        for (Entry<String, String> namespace : nsContext.allNamespaces()) {
            newRequestMessage.getSOAPHeader().addNamespaceDeclaration(namespace.getKey(), namespace.getValue());
            newRequestMessage.getSOAPBody().addNamespaceDeclaration(namespace.getKey(), namespace.getValue());
        }
        copyHeaders(requestMessage, newRequestMessage);
        requestMessage = newRequestMessage;
    }

    private void copyHeaders(SOAPMessage from, SOAPMessage to) {
        if (from == null) {
            return;
        }
        to.getMimeHeaders().removeAllHeaders();

        @SuppressWarnings("unchecked")
        Iterator<MimeHeader> headers = from.getMimeHeaders().getAllHeaders();
        while (headers.hasNext()) {
            MimeHeader header = headers.next();
            to.getMimeHeaders().addHeader(header.getName(), header.getValue());
        }
    }

    public String getXPath(String path) throws DOMException, XPathExpressionException, SOAPException {
        return xmlHelper.getXPathValue(path, responseMessage.getSOAPBody());
    }

    public String getXPathInHeader(String path) throws DOMException, XPathExpressionException, SOAPException {
        return xmlHelper.getXPathValue(path, responseMessage.getSOAPHeader());
    }

    public void setXPathValue(String path, String data) throws XPathExpressionException, SOAPException {
        xmlHelper.setXPathValue(path, data, requestMessage.getSOAPBody());
    }

    public void setXPathInHeaderValue(String path, String data) throws XPathExpressionException, SOAPException {
        xmlHelper.setXPathValue(path, data, requestMessage.getSOAPHeader());
    }

    public String request() throws TransformerException, SOAPException, IOException {
        return toString(requestMessage, MessagePartType.BODY);
    }

    public String response() throws TransformerException, SOAPException, IOException {
        return toString(responseMessage, MessagePartType.BODY);
    }

    public String requestHeader() throws TransformerException, SOAPException, IOException {
        return toString(requestMessage, MessagePartType.HEADER);
    }

    public String responseHeader() throws TransformerException, SOAPException, IOException {
        return toString(responseMessage, MessagePartType.HEADER);
    }

    public String fullRequest() throws TransformerException, SOAPException, IOException {
        return toString(requestMessage, MessagePartType.FULL);
    }

    public String fullResponse() throws TransformerException, SOAPException, IOException {
        return toString(responseMessage, MessagePartType.FULL);
    }

    public boolean soapFault() throws SOAPException {
        if (responseMessage == null || responseMessage.getSOAPBody() == null) {
            return true;
        }
        return responseMessage.getSOAPBody().getFault() != null;
    }

    private String toString(SOAPMessage message, MessagePartType messagePartType) throws SOAPException, TransformerException, IOException {
        if (message == null) {
            return null;
        }

        switch (messagePartType) {
            case HEADER:
                return normalize(message.getSOAPHeader());
            case BODY:
                return normalize(message.getSOAPBody());
            case FULL: {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                message.writeTo(out);
                return out.toString();
            }
            default:
                throw new IllegalStateException("Unknown message part type");
        }
    }

    private String normalize(SOAPElement element) throws TransformerException {
        StringWriter sw = new StringWriter();
        @SuppressWarnings("unchecked")
        Iterator<Element> elements = element.getChildElements();
        while (elements.hasNext()) {
            transformer.transform(new DOMSource(elements.next()), new StreamResult(sw));
        }
        return sw.toString();
    }

    public void setHeaderValue(String header, String value) {
        requestMessage.getMimeHeaders().setHeader(header, value);
    }

    public void addHeaderValue(String header, String value) {
        requestMessage.getMimeHeaders().addHeader(header, value);
    }

    public void resetHeaders() {
        requestMessage.getMimeHeaders().removeAllHeaders();
    }

    public String headers() {
        StringBuilder sb = new StringBuilder();
        @SuppressWarnings("unchecked")
        Iterator<MimeHeader> headers = requestMessage.getMimeHeaders().getAllHeaders();
        while (headers.hasNext()) {
            MimeHeader header = headers.next();
            sb.append("[" + header.getName() + "]");
            sb.append(" = ");
            sb.append("[" + header.getValue() + "]");
            if (headers.hasNext()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public void sendTo(String url) throws SOAPException {
        responseMessage = null;
        try {
            responseMessage = soapConnection.call(requestMessage, url);
        } finally {
            prepareForNewRequestMessage();
        }
    }

    public void addPrefixNamespace(String prefix, String namespace) throws SOAPException {
        requestMessage.getSOAPHeader().addNamespaceDeclaration(prefix.trim(), namespace.trim());
        requestMessage.getSOAPBody().addNamespaceDeclaration(prefix.trim(), namespace.trim());
        nsContext.addNamespacePrefix(prefix.trim(), namespace.trim());
    }

    public void resetNamespaces() {
        nsContext.clear();
    }

    enum MessagePartType {
        FULL,
        HEADER,
        BODY;
    }
}
