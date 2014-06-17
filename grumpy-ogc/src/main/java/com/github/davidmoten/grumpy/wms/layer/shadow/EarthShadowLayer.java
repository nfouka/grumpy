package com.github.davidmoten.grumpy.wms.layer.shadow;

import static com.github.davidmoten.grumpy.core.Position.position;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import com.github.davidmoten.grumpy.core.Position;
import com.github.davidmoten.grumpy.projection.FeatureUtil;
import com.github.davidmoten.grumpy.projection.Projector;
import com.github.davidmoten.grumpy.projection.ProjectorBounds;
import com.github.davidmoten.grumpy.projection.ProjectorTarget;
import com.github.davidmoten.grumpy.wms.Layer;
import com.github.davidmoten.grumpy.wms.RendererUtil;
import com.github.davidmoten.grumpy.wms.WmsRequest;
import com.github.davidmoten.grumpy.wms.WmsUtil;
import com.github.davidmoten.grumpy.wms.layer.shadow.Sun.Twilight;

public class EarthShadowLayer implements Layer {

    private static final double SUB_SOLAR_POINT_SIZE_PIXELS = 20.0;

    private static HashMap<Twilight, Color> shades = new HashMap<Twilight, Color>();

    static {
        shades.put(Twilight.NIGHT, Color.BLACK);
        shades.put(Twilight.ASTRONOMICAL, new Color(50, 50, 50));
        shades.put(Twilight.NAUTICAL, new Color(100, 100, 100));
        shades.put(Twilight.CIVIL, new Color(150, 150, 150));
        shades.put(Twilight.DAYLIGHT, Color.WHITE);
    }

    /**
     * Render the Earth's shadow onto the supplied graphics context
     * 
     * @param g
     *            - the graphics context used for rendering
     * @param projector
     *            - the projection used to map from the geo-spatial world onto
     *            the graphics context
     * @param bounds
     *            - the geo-spatial bounding box of the region to be rendered
     * @param width
     *            - of the graphics area in pixels
     * @param height
     *            - of the graphics area in pixels
     */
    private void render(Graphics g, Projector projector, Bounds bounds, int width, int height) {

        Graphics2D g2d = (Graphics2D) g;
        Position subSolarPoint = Sun.getPosition();
        renderSubSolarPoint(g2d, subSolarPoint, projector);
        renderTwilight(g2d, subSolarPoint, projector, bounds);
    }

    private static void renderSubSolarPoint(Graphics2D g, Position subSolarPoint,
            Projector projector) {

        Ellipse2D spot = new Ellipse2D.Double();
        g.setColor(Color.YELLOW);
        LatLon latLon = new LatLon(subSolarPoint.getLat(), subSolarPoint.getLon());
        Point point = projector.toPoint(latLon.lat(), latLon.lon());
        spot.setFrame(point.x - SUB_SOLAR_POINT_SIZE_PIXELS / 2, point.y
                - SUB_SOLAR_POINT_SIZE_PIXELS / 2, SUB_SOLAR_POINT_SIZE_PIXELS,
                SUB_SOLAR_POINT_SIZE_PIXELS);
        g.fill(spot);

    }

    private static void renderTwilight(Graphics2D g, Position subSolarPoint, Projector projector,
            Bounds geoBounds) {

        ProjectorTarget t = projector.getTarget();
        Rectangle xyBounds = new Rectangle(0, 0, t.getWidth(), t.getHeight());
        renderTwilightRegion(g, subSolarPoint, projector, geoBounds, xyBounds);
    }

    private static void renderTwilightRegion(Graphics2D g, Position subSolarPoint,
            Projector projector, Bounds geoBounds, Rectangle xyBounds) {

        // check if we need to divide the region

        final Twilight twilight;

        boolean regionDivisible = xyBounds.height > 1 || xyBounds.width > 1;

        if (!regionDivisible) {
            // region is indivisible, so choose any corner for the twilight
            // value
            twilight = Sun.getTwilight(subSolarPoint, new Position(geoBounds.getMin().lat(),
                    geoBounds.getMin().lon()));
        } else {
            // get the twilight value for the region if common to all sample
            // points in the region (if no common value returns null)
            twilight = Sun.getRegionUniformTwilightValue(geoBounds, subSolarPoint);
        }

        if (twilight != null) {
            // shade the region to represent the twilight
            shadeRegion(g, projector, geoBounds, twilight);
        } else {
            // region is a mix of twilight conditions and is divisble
            // so divide into sub regions ... 2 or 4
            // but only if we can

            renderTwilightOnSplitRegions(g, subSolarPoint, projector, xyBounds);
        }
    }

    private static void renderTwilightOnSplitRegions(Graphics2D g, Position subSolarPoint,
            Projector projector, Rectangle xyBounds) {
        // split region
        final Rectangle[] rectangles = splitRectangles(xyBounds);

        // now render each region
        for (Rectangle rect : rectangles) {
            Position min = projector.toPosition(rect.x, rect.y + rect.height);
            Position max = projector.toPosition(rect.x + rect.width, rect.y);
            Bounds bounds = new Bounds(new LatLon(min.getLat(), min.getLon()), new LatLon(
                    max.getLat(), max.getLon()));
            renderTwilightRegion(g, subSolarPoint, projector, bounds, rect);
        }
    }

    private static void shadeRegion(Graphics2D g, Projector projector, Bounds geoBounds,
            final Twilight twilight) {
        if (twilight != Twilight.DAYLIGHT) {

            List<Position> box = new ArrayList<Position>();
            box.add(position(geoBounds.getMin().lat(), geoBounds.getMin().lon()));
            box.add(position(geoBounds.getMin().lat(), geoBounds.getMax().lon()));
            box.add(position(geoBounds.getMax().lat(), geoBounds.getMax().lon()));
            box.add(position(geoBounds.getMax().lat(), geoBounds.getMin().lon()));
            box.add(position(geoBounds.getMin().lat(), geoBounds.getMin().lon()));

            // use multiple paths to handle boundary weirdness
            List<GeneralPath> path = RendererUtil.getPath(projector, box);

            // fill the region
            g.setColor(shades.get(twilight));
            RendererUtil.fill(g, path);
        }
    }

    private static Rectangle[] splitRectangles(Rectangle xyBounds) {
        final Rectangle[] rectangles;

        if (xyBounds.width > 1 && xyBounds.height > 1) {

            // divide into 4 sub regions

            rectangles = new Rectangle[4];
            int halfWidth = xyBounds.width / 2;
            int halfHeight = xyBounds.height / 2;
            rectangles[0] = new Rectangle(xyBounds.x, xyBounds.y, halfWidth, halfHeight);
            rectangles[1] = new Rectangle(xyBounds.x + halfWidth, xyBounds.y, xyBounds.width
                    - halfWidth, halfHeight);
            rectangles[2] = new Rectangle(xyBounds.x + halfWidth, xyBounds.y + halfHeight,
                    xyBounds.width - halfWidth, xyBounds.height - halfHeight);
            rectangles[3] = new Rectangle(xyBounds.x, xyBounds.y + halfHeight, halfWidth,
                    xyBounds.height - halfHeight);

        } else if (xyBounds.height > 1) {

            // divide into two vertically

            rectangles = new Rectangle[2];

            int halfHeight = xyBounds.height / 2;
            rectangles[0] = new Rectangle(xyBounds.x, xyBounds.y, 1, halfHeight);
            rectangles[1] = new Rectangle(xyBounds.x, xyBounds.y + halfHeight, 1, xyBounds.height
                    - halfHeight);

        } else {

            // divide into two horizontally

            rectangles = new Rectangle[2];
            int halfWidth = xyBounds.width / 2;
            rectangles[0] = new Rectangle(xyBounds.x, xyBounds.y, halfWidth, 1);
            rectangles[1] = new Rectangle(xyBounds.x + halfWidth, xyBounds.y, xyBounds.width
                    - halfWidth, 1);
        }
        return rectangles;
    }

    @Override
    public void render(Graphics2D g, WmsRequest request) {
        Projector projector = WmsUtil.getProjector(request);
        ProjectorBounds b = request.getBounds();
        Position min = FeatureUtil.convertToLatLon(b.getMinX(), b.getMinY(), request.getCrs());
        Position max = FeatureUtil.convertToLatLon(b.getMaxX(), b.getMaxY(), request.getCrs());
        Bounds bounds = new Bounds(new LatLon(min.getLat(), min.getLon()), new LatLon(max.getLat(),
                max.getLon()));
        render(g, projector, bounds, request.getWidth(), request.getHeight());
    }

    @Override
    public String getInfo(Date time, WmsRequest request, Point point, String format) {
        return null;
    }

}