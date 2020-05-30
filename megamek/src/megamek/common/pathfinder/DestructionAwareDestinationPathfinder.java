/*
* MegaMek -
* Copyright (C) 2020 The MegaMek Team
*
* This program is free software; you can redistribute it and/or modify it under
* the terms of the GNU General Public License as published by the Free Software
* Foundation; either version 2 of the License, or (at your option) any later
* version.
*
* This program is distributed in the hope that it will be useful, but WITHOUT
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
* FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
* details.
*/

package megamek.common.pathfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import megamek.client.bot.princess.AeroPathUtil;
import megamek.common.BulldozerMovePath;
import megamek.common.Coords;
import megamek.common.Entity;
import megamek.common.IBoard;
import megamek.common.MovePath;
import megamek.common.MovePath.MoveStepType;
import megamek.common.PlanetaryConditions;

/**
 * Handles the generation of ground-based move paths that contain information relating to the destruction 
 * of terrain necessary to accomplish that path.
 */
public class DestructionAwareDestinationPathfinder extends BoardEdgePathFinder {

    Comparator<BulldozerMovePath> movePathComparator;
    int maximumCost = Integer.MAX_VALUE;
    
    /**
     * Uses an A* search to find the "optimal" path to the destination coordinates.
     * Ignores move cost and makes note of hexes that need to be cleared for the path to
     * be viable.
     */
    public BulldozerMovePath findPathToCoords(Entity entity, Set<Coords> destinationCoords) {
        return findPathToCoords(entity, destinationCoords, false);
    }
    
    /**
     * Uses an A* search to find the "optimal" path to the destination coordinates.
     * Ignores move cost and makes note of hexes that need to be cleared for the path to
     * be viable.
     */
    public BulldozerMovePath findPathToCoords(Entity entity, Set<Coords> destinationCoords, boolean jump) {
        BulldozerMovePath startPath = new BulldozerMovePath(entity.getGame(), entity);
        
        if(entity.getDisplayName().contains("Zibler")) {
            int alpha = 1;
        }
        
        // if we're calculating a jump path and the entity has jump mp and can jump, start off with a jump
        // if we're trying to calc a jump path and the entity does not have jump mp, we're done
        if(jump && (startPath.getCachedEntityState().getJumpMPWithTerrain() > 0) &&
                !entity.isProne() && !entity.isHullDown() && 
                (entity.getGame().getPlanetaryConditions().getWindStrength() != PlanetaryConditions.WI_TORNADO_F4)) {
            startPath.addStep(MoveStepType.START_JUMP);
        // if we specified a jump path, but can't actually jump
        } else if (jump) {
            return null;
        // can't "climb into" anything while jumping
        } else { 
            if(entity.hasETypeFlag(Entity.ETYPE_INFANTRY)) {
                startPath.addStep(MoveStepType.CLIMB_MODE_OFF);
            } else {
                startPath.addStep(MoveStepType.CLIMB_MODE_ON);
            }
        }
        
        // if we're on the ground, let's try to get up first before moving 
        if(entity.isProne() || entity.isHullDown()) {
            startPath.addStep(MoveStepType.GET_UP);
        }

        Coords closest = getClosestCoords(destinationCoords, entity);
        // if we can't at all get to the coordinates with this entity, don't bother with the rest 
        if (closest == null) {
            return null;
        }
        
        movePathComparator = new AStarComparator(closest);
        maximumCost = Integer.MAX_VALUE;
        
        TreeSet<BulldozerMovePath> candidates = new TreeSet<>(movePathComparator);
        candidates.add(startPath);

        // a collection of coordinates we've already visited, so we don't loop back.
        Map<Coords, BulldozerMovePath> shortestPathsToCoords = new HashMap<>();
        shortestPathsToCoords.put(startPath.getFinalCoords(), startPath);
        BulldozerMovePath bestPath = null;

        while(!candidates.isEmpty()) {
            BulldozerMovePath currentPath = candidates.pollFirst();
            
            if(currentPath.getFinalCoords().getX() == 9 &&
                    currentPath.getFinalCoords().getY() == 59) {
                int alpha = 1;
            }
            
            candidates.addAll(generateChildNodes(currentPath, shortestPathsToCoords));
            
            if(destinationCoords.contains(currentPath.getFinalCoords()) &&
                    (bestPath == null || movePathComparator.compare(bestPath, currentPath) < 0)) {
                bestPath = currentPath;
                maximumCost = bestPath.getMpUsed() + bestPath.getLevelingCost();
            }
        }
  
        return bestPath;
    }
    
    /**
     * Calculates the closest coordinates to the given entity
     * Coordinates which you have to blow up to get into are considered to be further
     */
    public static Coords getClosestCoords(Set<Coords> destinationRegion, Entity entity) {
        Coords bestCoords = null;
        int bestDistance = Integer.MAX_VALUE;
        
        for(Coords coords : destinationRegion) {
            if(!entity.getGame().getBoard().contains(coords)) {
                continue;
            }
            
            int levelingCost = BulldozerMovePath.calculateLevelingCost(coords, entity);
            boolean canLevel = levelingCost > BulldozerMovePath.CANNOT_LEVEL;
            
            if(!entity.isLocationProhibited(coords) || canLevel) {
                int distance = coords.distance(entity.getPosition()) + (canLevel ? levelingCost : 0);
                if(distance < bestDistance) {
                    bestDistance = distance;
                    bestCoords = coords;
                }
            }
        }
        
        return bestCoords;
    }
    
    /**
     * Function that generates all possible "legal" moves resulting from the given path
     * and updates the set of visited coordinates so we don't visit them again.
     * @param parentPath The path for which to generate child nodes
     * @param visitedCoords Set of visited coordinates so we don't loop around
     * @return List of valid children. Between 0 and 3 inclusive.
     */
    protected List<BulldozerMovePath> generateChildNodes(BulldozerMovePath parentPath, Map<Coords, BulldozerMovePath> shortestPathsToCoords) {
        List<BulldozerMovePath> children = new ArrayList<>();

        // there are six possible children of a move path, defined in AeroPathUtil.TURNS
        for(List<MoveStepType> turns : AeroPathUtil.TURNS) {
            BulldozerMovePath childPath = (BulldozerMovePath) parentPath.clone();
            
            // apply the list of turn steps
            for(MoveStepType stepType : turns) {
                childPath.addStep(stepType);
            }
            
            // potentially apply UP so we can hop over unwanted terrain
            PathDecorator.AdjustElevationForForwardMovement(childPath);
            
            // move forward and process the generated child path
            childPath.addStep(MoveStepType.FORWARDS);
            processChild(childPath, children, shortestPathsToCoords);
        }

        return children;
    }
    
    /**
     * Helper function that handles logic related to potentially adding a generated child path
     * to the list of child paths.
     */
    protected void processChild(BulldozerMovePath child, List<BulldozerMovePath> children, 
            Map<Coords, BulldozerMovePath> shortestPathsToCoords) {
        int alpha = 1;
        // (if we haven't visited these coordinates before
        // or we have, and this is a shorter path)
        // and (it is a legal move
        // or it needs some "terrain adjustment" to become a legal move)
        // and we haven't already found a path to the destination that's cheaper than what we're considering
        // and we're not going off board 
        MoveLegalityIndicator mli = isLegalMove((MovePath) child);
        
        // if this path goes through terrain that can be leveled
        // but has other problems with it (e.g. elevation change, or the "reduced" terrain still won't let you through)
        // it still can't be leveled
        boolean canLevel = child.needsLeveling() &&
                !mli.outOfBounds &&
                !mli.destinationImpassable &&
                !mli.goingDownTooLow &&
                !mli.goingUpTooHigh &&
                !mli.wheeledTankRestriction &&
                !mli.destinationHasWeakBridge &&
                !mli.groundTankIntoWater;
        
        if((!shortestPathsToCoords.containsKey(child.getFinalCoords()) ||
                // shorter path to these coordinates
                (movePathComparator.compare(shortestPathsToCoords.get(child.getFinalCoords()), child) > 0)) &&
                // legal or needs leveling and not off-board
                (mli.isLegal() || canLevel) &&
                // better than existing path to ultimate destination
                (child.getMpUsed() + child.getLevelingCost() < maximumCost)) {
            shortestPathsToCoords.put(child.getFinalCoords(), child);
            children.add(child);
        }
    }
    
    /**
     * Comparator implementation useful in comparing how much closer a given path is to the internal
     * "destination edge" than the other.
     * @author NickAragua
     *
     */
    private class AStarComparator implements Comparator<BulldozerMovePath> {
        private Coords destination;

        /**
         * Constructor - initializes the destination edge.
         * @param targetRegion Destination edge
         */
        public AStarComparator(Coords destination) {
            this.destination = destination;
        }
        
        /**
         * compare the first move path to the second
         * Favors paths that move closer to the destination edge first.
         * in case of tie, favors paths that cost less MP
         */
        public int compare(BulldozerMovePath first, BulldozerMovePath second) {            
            IBoard board = first.getGame().getBoard();
            boolean backwards = false;
            int h1 = first.getFinalCoords().distance(destination)
                    + ShortestPathFinder.getLevelDiff(first, destination, board)
                    + ShortestPathFinder.getElevationDiff(first, destination, board, first.getEntity());
            int h2 = second.getFinalCoords().distance(destination)
                    + ShortestPathFinder.getLevelDiff(second, destination, board)
                    + ShortestPathFinder.getElevationDiff(second, destination, board, second.getEntity());
    
            int dd = (first.getMpUsed() + first.getLevelingCost() + first.getAdditionalCost() + h1) 
                    - (second.getMpUsed() + second.getLevelingCost() + second.getAdditionalCost() + h2);
            
            // getFacingDiff returns a number between 0 and 3 inclusive. 
            // if the value diff is larger than 3, then it won't make a difference and we skip calculating it
            if(Math.abs(dd) < 4) 
            {
                dd += ShortestPathFinder.getFacingDiff(first, destination, backwards);
                dd -= ShortestPathFinder.getFacingDiff(second, destination, backwards);
            }
    
            if (dd != 0) {
                return dd;
            } else {
                return first.getHexesMoved() - second.getHexesMoved();
            }           
        }
    }
}
