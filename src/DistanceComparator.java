// $Id$
/*
 * CraftBook
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

import com.sk89q.craftbook.server.PointBasedEntity;
import com.sk89q.craftbook.util.Vector;

import java.util.Comparator;

/**
 * Used to compare ComplexBlocks based on distance to a point.
 *
 * @author sk89q
 */
public class DistanceComparator<T extends PointBasedEntity>
    implements Comparator<PointBasedEntity> {
    /**
     * Origin to compare from.
     */
    private Vector origin;

    /**
     * Construct the object.
     * 
     * @param origin
     */
    public DistanceComparator(Vector origin) {
        this.origin = origin;
    }

    /**
     * Compares two objects.
     * 
     * @param o1
     * @param o2
     * @return
     */
    public int compare(PointBasedEntity o1, PointBasedEntity o2) {
        PointBasedEntity b1 = (PointBasedEntity)o1;
        PointBasedEntity b2 = (PointBasedEntity)o2;

        double dist1 = b1.getPosition().distance(origin);
        double dist2 = b2.getPosition().distance(origin);

        if (dist1 < dist2) {
            return -1;
        } else if (dist1 > dist2) {
            return 1;
        } else {
            return 0;
        }
    }
}
