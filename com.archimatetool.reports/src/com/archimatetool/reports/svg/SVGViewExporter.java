package com.archimatetool.reports.svg;

import java.awt.Graphics2D;

/* 
 * Largely pillaged from SVGExportProvider
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.LayerConstants;
import org.eclipse.gef.editparts.LayerManager;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.archimatetool.editor.diagram.util.DiagramUtils;
import com.archimatetool.editor.ui.ImageFactory;
import com.archimatetool.export.svg.ExtendedGraphicsToGraphics2DAdaptor;
import com.archimatetool.export.svg.Messages;
import com.archimatetool.export.svg.graphiti.GraphicsToGraphics2DAdaptor;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.reports.rst.BoundsWithAbsolutePosition;

public class SVGViewExporter  {
    protected IFigure fFigure;
    protected Image image;
    protected IDiagramModel model;
    
    protected String svgNS = "http://www.w3.org/2000/svg"; //$NON-NLS-1$
    
    /**
     * Map of new bounds for each diagram for bounds offset
     */
    private Map<IDiagramModel, Rectangle> diagramBoundsMap = new HashMap<IDiagramModel, Rectangle>();

    /**
     * Map of new bounds for child objects in images for hit areas
     */
    private Map<String, BoundsWithAbsolutePosition> childBoundsMap = new HashMap<String, BoundsWithAbsolutePosition>();
    
    public SVGViewExporter(IDiagramModel dm) {
    	model = dm;
	}
    
	/**
     * Create the DOM SDocument with root namespace and root element name
     * @return The DOM Document to save to
     */
    protected Document createDocument() {
        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
        return domImpl.createDocument(svgNS, "svg", null); //$NON-NLS-1$
    }
    
    /**
     * Create a SVGGeneratorContext and set its attributes
     * @param document The DOM Document
     * @param embeddedFonts If true will embed fonts
     * @return The SVGGeneratorContext
     */
    protected SVGGeneratorContext createContext(Document document, boolean embedFonts) {
        SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document);
        ctx.setEmbeddedFontsOn(embedFonts);
        ctx.setComment(Messages.SVGExportProvider_1); // Add a comment
        return ctx;
    }
    
    public void export(File file) throws IOException {
    	/* This process is pretty similar to DiagramUtils.createModelReferencedImage
    	 * 
    	 */
    
        // I want the figure, not the image. . . 
    	//  Based on: ModelReferencedImage geoImage = DiagramUtils.createModelReferencedImage(model, 1, 10);
        Shell shell = new Shell();
        shell.setLayout(new FillLayout());
        
        GraphicalViewer viewer = DiagramUtils.createViewer(model, shell);
        LayerManager layerManager = (LayerManager)viewer.getEditPartRegistry().get(LayerManager.ID);
        IFigure fFigure = layerManager.getLayer(LayerConstants.PRINTABLE_LAYERS);

        shell.dispose();

        // Create a DOM Document
        Document document = createDocument();
        
        // Create a context for customisation // Embed fonts is the boolean.
        SVGGeneratorContext ctx = createContext(document, true);
        
        // Create a Batik SVGGraphics2D instance
        SVGGraphics2D svgGenerator = new SVGGraphics2D(ctx, false);
        
        // Get the outer bounds of the figure
        Rectangle bounds = getViewportBounds(fFigure);

        // Create a Graphiti wrapper adapter (does this pretend to be a graphics adapter?)
        GraphicsToGraphics2DAdaptor graphicsAdaptor = createGraphicsToGraphics2DAdaptor(svgGenerator, bounds);
        
        // Paint the figure onto the graphics instance (And this paints?)
        fFigure.paint(graphicsAdaptor);
        
        // Get the Element root from the SVGGraphics2D instance
        Element root = svgGenerator.getRoot();

        // TODO: Add svg href's here.
        // process the children
        for(IDiagramModelObject dmo: model.getChildren() ) {
        	IBounds dmoBounds = dmo.getBounds();
//            addNewBounds(dmo, dmoBounds.x * -1, dmoBounds.y * -1);
            Element a = document.createElementNS(svgNS, "a");
            
            // TODO: Need to compute the relative href for the dmo id. How??
            EObject container = dmo.eContainer();  // This seems to be the view, not the folder.
            a.setAttribute("href", "#"+dmo.getId());

            Element rect = document.createElementNS(svgNS, "rect");
            rect.setAttribute("x", Integer.toString(dmoBounds.getX() - bounds.x));
            rect.setAttribute("y", Integer.toString(dmoBounds.getY() - bounds.y));
            rect.setAttribute("width", Integer.toString(dmoBounds.getWidth()));
            rect.setAttribute("height", Integer.toString(dmoBounds.getHeight()));
            rect.setAttribute("fill-opacity", "0");
            rect.setAttribute("stroke", "blue");
            a.appendChild(rect);
            
            root.appendChild(a);
        }

       
//        setViewBoxAttribute(root, bounds);
        root.setAttributeNS(null,  "viewBox",  "-5 -5 " + bounds.width + " " + bounds.height);
        
        // Save the root element
        Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8"); //$NON-NLS-1$
        svgGenerator.stream(root, out);
        
        // Close
        graphicsAdaptor.dispose();
        out.close();
    }

    /* 
     * These two classes were lovingly recycled from com.archimate.export  
     */
    
    /**
     * Get the viewport bounds for the given figure that will be printed
     * @param figure the given figure that will be printed
     * @return The bounds
     */
    protected Rectangle getViewportBounds(IFigure figure) {
        Rectangle rect = DiagramUtils.getMinimumBounds(figure);
        if(rect == null) {
            rect = new Rectangle(0, 0, 100, 100); // At least a minimum for a blank image
        }
        else {
            rect.expand(10, 10); // margins
        }
        return rect;
    }

    /**
     * Create the Graphiti Graphics2D adapter with its Viewport
     * @param graphics2d The Batick AWT Graphics2D to wrap
     * @param viewPort The Viewport of the figure to print
     * @return The GraphicsToGraphics2DAdaptor
     */
    protected GraphicsToGraphics2DAdaptor createGraphicsToGraphics2DAdaptor(Graphics2D graphics2d, Rectangle viewPort) {
        ExtendedGraphicsToGraphics2DAdaptor graphicsAdaptor = new ExtendedGraphicsToGraphics2DAdaptor(graphics2d, viewPort);
        graphicsAdaptor.translate(viewPort.x * -1, viewPort.y * -1);
        graphicsAdaptor.setClip(viewPort); // need to do this
        graphicsAdaptor.setAdvanced(true);
        return graphicsAdaptor;
    }


    /**
     * Add new bounds for each diagram object in relation to its parent offset x,y
     */
    private void addNewBounds(IDiagramModelObject dmo, int offsetX, int offsetY) {
        // Add new bounds caled to device zoom
        BoundsWithAbsolutePosition newBounds = new BoundsWithAbsolutePosition(dmo.getBounds(), ImageFactory.getImageDeviceZoom() / 100);
        newBounds.setOffset(offsetX, offsetY); // Add offset
        childBoundsMap.put(dmo.getId(), newBounds);
        
        // Children
        if(dmo instanceof IDiagramModelContainer) {
            for(IDiagramModelObject child: ((IDiagramModelContainer)dmo).getChildren() ) {
                addNewBounds(child, newBounds.getX1(), newBounds.getY1());
            }
        }
    }

    
}
