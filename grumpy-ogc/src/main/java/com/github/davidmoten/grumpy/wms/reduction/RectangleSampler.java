package com.github.davidmoten.grumpy.wms.reduction;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import com.github.davidmoten.grumpy.projection.Projector;

public interface RectangleSampler {

    List<Point> sample(Rectangle region, Projector projector);

}
