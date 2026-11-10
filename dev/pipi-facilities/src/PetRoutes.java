package dev.pipi.facilities;
import java.util.*;
/** Bounded A*; no diagonal corner cutting, at most one block up/down per step. */
final class PetRoutes {
 record Cell(int x,int y,int z){}
 interface Terrain {boolean open(Cell cell);}
 private record Step(Cell cell,double cost,double estimate){}
 static List<Cell> find(Cell start,Cell target,Terrain terrain){
  Set<Cell> goals=new HashSet<>();if(terrain.open(target))goals.add(target);else for(int r=1;r<=2&&goals.isEmpty();r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(Math.max(Math.abs(x),Math.abs(z))==r)for(int y:new int[]{0,1,-1}){Cell p=new Cell(target.x+x,target.y+y,target.z+z);if(terrain.open(p))goals.add(p);}
  if(goals.isEmpty())return List.of();
  Map<Cell,Double> costs=new HashMap<>();Map<Cell,Cell> previous=new HashMap<>();Set<Cell> closed=new HashSet<>();PriorityQueue<Step> queue=new PriorityQueue<>(Comparator.comparingDouble(Step::estimate));costs.put(start,0d);queue.add(new Step(start,0,heuristic(start,goals)));int visited=0;
  while(!queue.isEmpty()&&visited++<4096){var next=queue.poll();Cell at=next.cell;if(!closed.add(at))continue;if(goals.contains(at)){LinkedList<Cell> path=new LinkedList<>();for(Cell c=at;!c.equals(start);c=previous.get(c))path.addFirst(c);if(path.isEmpty())path.add(at);return path;}
   for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){if(dx==0&&dz==0)continue;for(int dy:new int[]{0,1,-1}){Cell p=new Cell(at.x+dx,at.y+dy,at.z+dz);if(Math.abs(p.x-start.x)>64||Math.abs(p.z-start.z)>64||Math.abs(p.y-start.y)>12||closed.contains(p)||!terrain.open(p))continue;if(dx!=0&&dz!=0&&(!terrain.open(new Cell(at.x+dx,at.y+dy,at.z))||!terrain.open(new Cell(at.x,at.y+dy,at.z+dz))))continue;double cost=next.cost+(dx!=0&&dz!=0?1.414:1)+Math.abs(dy)*.7;if(cost<costs.getOrDefault(p,Double.MAX_VALUE)){costs.put(p,cost);previous.put(p,at);queue.add(new Step(p,cost,cost+heuristic(p,goals)));}break;}}
  }return List.of();
 }
 private static double heuristic(Cell p,Set<Cell> goals){double best=Double.MAX_VALUE;for(Cell g:goals)best=Math.min(best,Math.hypot(p.x-g.x,p.z-g.z)+Math.abs(p.y-g.y)*.7);return best;}
}
