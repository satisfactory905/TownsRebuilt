package xaos.utils;

import java.util.ArrayList;

import xaos.main.World;
import xaos.tiles.Cell;

public final class AStarNodo extends Point3DShort implements Comparable<AStarNodo> {

    private static final long serialVersionUID = 3814601651795128806L;

    // Pool
    public static final int MAX_POOL = 1024;
    public static ArrayList<AStarNodo> alPool = new ArrayList<AStarNodo>();

    // Referencia al padre
    private AStarNodo padre;

    private int f; // Suma de g y h
    private int g; // Coste hasta llegar a este nodo
//	private int h; // Heuristica (o cuanto nos falta para llegar)

    public AStarNodo() {
    }

    public AStarNodo(int x, int y, int z, AStarNodo padre, int xDest, int yDest, int zDest) {
        generateAll(x, y, z, padre, xDest, yDest, zDest);
    }

    public void generateAll(int x, int y, int z, AStarNodo padre, int xDest, int yDest, int zDest) {
        setPoint((short) x, (short) y, (short) z);

        this.padre = padre;

        int h = Math.max(Math.abs(xDest - x), Math.abs(yDest - y));
        if (z >= (World.MAP_NUM_LEVELS_OUTSIDE - 1)) {
            if (zDest >= (World.MAP_NUM_LEVELS_OUTSIDE - 1)) {
                // Los 2 puntos son underground
                h += ((32) * Math.abs(zDest - z)); // Metodo Manhattan, funciona mejor incluso permitiendo movimiento diagonal que la "Distancia Chebyshev" (20% mejor)
            } else {
                // El origen es underground pero el destino no
                h += ((32) * Math.abs(z - (World.MAP_NUM_LEVELS_OUTSIDE - 1)) + 2 * (World.MAP_NUM_LEVELS_OUTSIDE - 1 - zDest)); // Metodo Manhattan, funciona mejor incluso permitiendo movimiento diagonal que la "Distancia Chebyshev" (20% mejor)
            }
        } else {
            // El origen es outside
            if (zDest >= (World.MAP_NUM_LEVELS_OUTSIDE - 1)) {
                // El destino es underground (y origen outside)
                h += ((32) * Math.abs(zDest - (World.MAP_NUM_LEVELS_OUTSIDE - 1)) + 2 * (World.MAP_NUM_LEVELS_OUTSIDE - 1 - z)); // Metodo Manhattan, funciona mejor incluso permitiendo movimiento diagonal que la "Distancia Chebyshev" (20% mejor)
            } else {
                // El destino es outside, como el origen
                h += (2 * Math.abs(zDest - z)); // Metodo Manhattan, funciona mejor incluso permitiendo movimiento diagonal que la "Distancia Chebyshev" (20% mejor)
//				h += (8 * Math.abs (zDest - z)); // Metodo Manhattan, funciona mejor incluso permitiendo movimiento diagonal que la "Distancia Chebyshev" (20% mejor)
//				h += (4 * Math.abs (zDest - z)); // Metodo Manhattan, funciona mejor incluso permitiendo movimiento diagonal que la "Distancia Chebyshev" (20% mejor)
            }
        }

        h = h * 100;
        //f -= (100 - Cell.getCellWalkNeeded (x, y, z));
        h -= (100 - Cell.getCellWalkNeeded(x, y, z));

        if (padre != null) {
			//g = padre.g + (100 - Cell.getCellWalkNeeded (x, y, z));
            //g = padre.g + 100;
            g = (padre.g + Cell.getCellWalkNeeded(padre.x, padre.y, padre.z));
			//f += (padre.g + Cell.getCellWalkNeeded (padre.x, padre.y, padre.z));
            //f += g;
        } else {
            g = 0;
        }

        f = g + h;
    }

    public static AStarNodo getPoolInstance(int x, int y, int z, AStarNodo padre, int xDest, int yDest, int zDest) {
        synchronized (alPool) {
            if (alPool.size() > 0) {
//				System.out.println ("Pilla  [" + alPool.size () + "]");
                AStarNodo nodo = alPool.remove(alPool.size() - 1);
                nodo.generateAll(x, y, z, padre, xDest, yDest, zDest);
                return nodo;
            } else {
//				System.out.println ("Nuevo  [" + alPool.size () + "]");
                return new AStarNodo(x, y, z, padre, xDest, yDest, zDest);
            }
        }
    }

    public static void returnToPool(AStarNodo nodo) {
        synchronized (alPool) {
//			System.out.println ("Return [" + alPool.size () + "]");
            if (alPool.size() < MAX_POOL) {
                alPool.add(nodo);
            }
        }
    }

    public AStarNodo getPadre() {
        return padre;
    }

    public int getF() {
        return f;
    }

    public int getG() {
        return g;
    }

    public int compareTo(AStarNodo o) {
        return f - o.f;
    }

    public boolean equals(AStarNodo nodo) {
        return (x == nodo.x && y == nodo.y && z == nodo.z);
    }
}
