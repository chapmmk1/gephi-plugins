/*
Copyright 2008 WebAtlas
Authors : Mathieu Bastian, Mathieu Jacomy, Julian Bilcke
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gephi.streaming.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.gephi.data.attributes.api.AttributeRow;
import org.gephi.data.attributes.api.AttributeValue;
import org.gephi.data.properties.PropertiesColumn;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.Node;
import org.gephi.streaming.api.CompositeOperationSupport;
import org.gephi.streaming.api.GraphUpdaterOperationSupport;
import org.gephi.streaming.api.StreamReader;
import org.gephi.streaming.api.StreamReaderFactory;
import org.gephi.streaming.api.StreamWriter;
import org.gephi.streaming.api.StreamWriterFactory;
import org.openide.util.Lookup;

/**
 * @author panisson
 *
 */
public class ServerOperationExecutor {
    
    private final GraphBufferedOperationSupport graphBufferedOperationSupport;
    private final GraphUpdaterOperationSupport graphUpdaterOperationSupport;
    private final Graph graph;
    private final StreamWriterFactory writerFactory;
    private final StreamReaderFactory readerFactory;
    private boolean sendVizData = true;
    
    public ServerOperationExecutor(Graph graph) {
        graphBufferedOperationSupport = new GraphBufferedOperationSupport(graph);
        graphUpdaterOperationSupport = new GraphUpdaterOperationSupport(graph);
        this.graph = graph;
        writerFactory = Lookup.getDefault().lookup(StreamWriterFactory.class);
        readerFactory = Lookup.getDefault().lookup(StreamReaderFactory.class);
    }
    
    /**
     * Test with <br>
     * curl http://localhost:8080/graphstream?operation=getGraph&format=DGS
     * 
     * @param format
     * @param outputStream
     */
    public void executeGetGraph(String format, OutputStream outputStream) {
        StreamWriter writer = writerFactory.createStreamWriter(format, outputStream);
        writer.startStream();
        
        graphBufferedOperationSupport.addOperationSupport(writer);
    }
     
    /**
     * Test with <br>
     * curl "http://localhost:8080/graphstream?operation=getNode&id=0201485419&format=DGS"
     * 
     * @param id
     * @param format
     * @param outputStream
     * @throws IOException
     */
    public void executeGetNode(String id, String format, OutputStream outputStream) throws IOException {
        StreamWriter writer = writerFactory.createStreamWriter(format, outputStream);
        writer.startStream();
        
        graph.readLock();
        try {
            Node node = graph.getNode(id);
            if (node != null) {
                String nodeId = node.getNodeData().getId();
                writer.nodeAdded(nodeId, getNodeAttributes(node));
            }
        } finally {
            graph.readUnlock();
        }
        
        writer.endStream();
        outputStream.close();
    }
    
    /**
     * Test with <br>
     * curl http://localhost:8080/graphstream?operation=getEdge&id=0201485419_0321335708&format=DGS
     * 
     * @param id
     * @param format
     * @param outputStream
     * @throws IOException
     */
    public void executeGetEdge(String id, String format, OutputStream outputStream) throws IOException {
        
        StreamWriter writer = writerFactory.createStreamWriter(format, outputStream);
        writer.startStream();
        
        graph.readLock();
        try {
            Edge edge = graph.getEdge(id);
            if (edge != null) {
                String edgeId = edge.getEdgeData().getId();
                String sourceId = edge.getSource().getNodeData().getId();
                String targetId = edge.getTarget().getNodeData().getId();
                writer.edgeAdded(edgeId, sourceId, targetId, edge.isDirected(), getEdgeAttributes(edge));
            }
        } finally {
            graph.readUnlock();
        }
        writer.endStream();
        outputStream.close();
    }
    
    /**
     * Test with: <br>
     * curl "http://localhost:8080/graphstream?operation=updateGraph&format=DGS" -d "DGS004<br>
     * updatedObjects  0 0<br>
     * an 1111"
     * 
     * <p>The entire graph can be loaded using<br>
     * curl "http://localhost:8080/graphstream?operation=updateGraph&format=DGS" --data-binary @amazon.dgs
     * 
     * 
     * @param format
     * @param inputStream
     * @param outputStream
     * @throws IOException
     */
    public void executeUpdateGraph(String format, InputStream inputStream, OutputStream outputStream) 
    throws IOException {
        
//        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
//
//        String line;
//        while ((line=reader.readLine())!=null)
//            System.out.println(line);
//        outputStream.close();
        
        StreamWriter writer = writerFactory.createStreamWriter(format, outputStream);
        CompositeOperationSupport cos = new CompositeOperationSupport();
        cos.addOperationSupport(graphUpdaterOperationSupport);
        cos.addOperationSupport(writer);
        
        StreamReader reader = readerFactory.createStreamReader(format, cos);
        reader.processStream(inputStream);
        outputStream.close();
    }
    
    private Map<String, Object> getNodeAttributes(Node node) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        AttributeRow row = (AttributeRow) node.getNodeData().getAttributes();

        if (row != null)
            for (AttributeValue attributeValue: row.getValues()) {
                if (attributeValue.getColumn().getIndex()!=PropertiesColumn.NODE_ID.getIndex()
                        && attributeValue.getValue()!=null)
                    attributes.put(attributeValue.getColumn().getTitle(), attributeValue.getValue());
            }

        if (sendVizData) {
            attributes.put("x", node.getNodeData().x());
            attributes.put("y", node.getNodeData().y());
            attributes.put("z", node.getNodeData().z());

            attributes.put("r", node.getNodeData().r());
            attributes.put("g", node.getNodeData().g());
            attributes.put("b", node.getNodeData().b());

            attributes.put("size", node.getNodeData().getSize());
        }

        return attributes;
    }

    private Map<String, Object> getEdgeAttributes(Edge edge) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        AttributeRow row = (AttributeRow) edge.getEdgeData().getAttributes();
        if (row != null)
            for (AttributeValue attributeValue: row.getValues()) {
                if (attributeValue.getColumn().getIndex()!=PropertiesColumn.EDGE_ID.getIndex()
                        && attributeValue.getValue()!=null)
                     attributes.put(attributeValue.getColumn().getTitle(), attributeValue.getValue());
            }

        if (sendVizData) {
            
            attributes.put("r", edge.getEdgeData().r());
            attributes.put("g", edge.getEdgeData().g());
            attributes.put("b", edge.getEdgeData().b());
            
            attributes.put("weight", edge.getWeight());
        }

        return attributes;
    }
}
