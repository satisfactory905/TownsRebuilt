package xaos.utils;

import java.util.ArrayList;

import xaos.main.Game;
import xaos.main.World;
import xaos.tiles.Cell;
import xaos.tiles.entities.living.LivingEntity;
import xaos.tiles.terrain.Terrain;

public final class AStarQueueItem {

    private int livingEntityID;
    private int livingEntityType;
    private Point3DShort startPoint;
    private Point3DShort endPoint;

    private boolean finished;
    private ArrayList<Point3DShort> path;

    private static boolean closedList[][][];
    public static short openListIndexes[][][];
    private AStarBinaryHeap openList;
    private boolean iniToEnd;

    private int numCellsToRemove;

    private boolean newItem;
    private boolean siege;

    public AStarQueueItem(int livingEntityID, int livingEntityType, Point3DShort startPoint, Point3DShort endPoint, boolean bSiege) {
        setLivingEntityID(livingEntityID);
        setLivingEntityType(livingEntityType);
        setStartPoint(startPoint);
        setEndPoint(endPoint);
        setNewItem(true);
        setSiege(bSiege);
    }

    public int getLivingEntityID() {
        return livingEntityID;
    }

    public void setLivingEntityID(int livingEntityID) {
        this.livingEntityID = livingEntityID;
    }

    /**
     * @param livingEntityType the livingEntityType to set
     */
    public void setLivingEntityType(int livingEntityType) {
        this.livingEntityType = livingEntityType;
    }

    /**
     * @return the livingEntityType
     */
    public int getLivingEntityType() {
        return livingEntityType;
    }

    public Point3DShort getStartPoint() {
        return startPoint;
    }

    public void setStartPoint(Point3DShort startPoint) {
        this.startPoint = startPoint;
    }

    public Point3DShort getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(Point3DShort endPoint) {
        this.endPoint = endPoint;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public static boolean[][][] getClosedList() {
        return closedList;
    }

    public static void setClosedList(boolean[][][] list) {
        closedList = list;
    }

    public AStarBinaryHeap getOpenList() {
        return openList;
    }

    public void setOpenList(AStarBinaryHeap openList) {
        this.openList = openList;
    }

    public boolean isIniToEnd() {
        return iniToEnd;
    }

    public void setIniToEnd(boolean iniToEnd) {
        this.iniToEnd = iniToEnd;
    }

    /**
     * @param numCellsToRemove the numCellsToRemove to set
     */
    public void setNumCellsToRemove(int numCellsToRemove) {
        this.numCellsToRemove = numCellsToRemove;
    }

    /**
     * @return the numCellsToRemove
     */
    public int getNumCellsToRemove() {
        return numCellsToRemove;
    }

    public void setNewItem(boolean newItem) {
        this.newItem = newItem;
    }

    public boolean isNewItem() {
        return newItem;
    }

    public void setSiege(boolean siege) {
        this.siege = siege;
    }

    public boolean isSiege() {
        return siege;
    }

    public ArrayList<Point3DShort> getPath() {
        return path;
    }

    public void setPath(ArrayList<Point3DShort> path) {
        this.path = path;
    }

    /**
     * Continua (o empieza) la busqueda A* hasta un maximo de "iMaxIterations"
     * iteraciones.
     *
     * @param iMaxIterations
     * @return el numero de iteraciones efectuadas
     */
    public int search(int iMaxIterations) {
        // Miramos si las 2 celdas aun estan en la misma zona (por si acaso alguien ha digado o hecho algo mientras buscaba)
        if (World.getCell(startPoint).getAstarZoneID() != World.getCell(endPoint).getAstarZoneID()) {
			// Cagada, ASZI distinto, finished task!
            //setClosedList (null);
            setFinished(true);
            return 0;
        }

		// Miramos si es una continuacion
        //boolean bContinue = getClosedList () != null;
        AStarNodo nodo = null;
        if (isNewItem()) {
            setNewItem(false);
			// Miramos las casillas cercanas al punto final y origen, empezaremos la busqueda por el que tenga mas
            // restricciones, o sea, el que tenga mas cells-NO-Allowed
            // Segun que ruta podemos mejorar la velocidad de busqueda en un grado 10.000 a 1 (aprox.)

            // Miramos en un cuadro de 7x7 y contamos las NO allowed para cada punto
            int iNOAllowedInicial = 0;
            int iNOAllowedFinal = 0;
            for (int x = -3; x <= 3; x++) {
                for (int y = -3; y <= 3; y++) {
                    if (!isAllowed(startPoint.x + x, startPoint.y + y, startPoint.z)) {
                        iNOAllowedInicial++;
                    }
                    if (!isAllowed(endPoint.x + x, endPoint.y + y, endPoint.z)) {
                        iNOAllowedFinal++;
                    }
                }
            }

            if (iNOAllowedFinal > iNOAllowedInicial) {
                // Intercambiamos los puntos inicial y final para empezar a buscar desde el final
                short auxX = startPoint.x;
                short auxY = startPoint.y;
                short auxZ = startPoint.z;
                startPoint.x = endPoint.x;
                startPoint.y = endPoint.y;
                startPoint.z = endPoint.z;
                endPoint.x = auxX;
                endPoint.y = auxY;
                endPoint.z = auxZ;
            }

            setIniToEnd(!(iNOAllowedFinal > iNOAllowedInicial));

            // Seteamos la lista cerrada
            if (getClosedList() == null || getClosedList()[0][0].length != (Game.getWorld().getNumFloorsDiscovered() + 1)) {
                setClosedList(new boolean[World.MAP_WIDTH][World.MAP_HEIGHT][Game.getWorld().getNumFloorsDiscovered() + 1]);
                openListIndexes = new short[World.MAP_WIDTH][World.MAP_HEIGHT][Game.getWorld().getNumFloorsDiscovered() + 1];
                // Limpiamos open indexes
                for (int x = 0; x < World.MAP_WIDTH; x++) {
                    for (int y = 0; y < World.MAP_HEIGHT; y++) {
                        for (int z = 0; z < getClosedList()[0][0].length; z++) {
                            openListIndexes[x][y][z] = -1;
                        }
                    }
                }
            } else {
                // Limpiamos la closed y la open indexes
                for (int x = 0; x < World.MAP_WIDTH; x++) {
                    for (int y = 0; y < World.MAP_HEIGHT; y++) {
                        for (int z = 0; z < getClosedList()[0][0].length; z++) {
                            getClosedList()[x][y][z] = false;
                            openListIndexes[x][y][z] = -1;
                        }
                    }
                }
            }

            // Anadimos el primer nodo en la lista abierta
            nodo = AStarNodo.getPoolInstance(startPoint.x, startPoint.y, startPoint.z, null, endPoint.x, endPoint.y, endPoint.z);
            openList = new AStarBinaryHeap();
            openList.add(nodo);
        }

        // Bucle principal A*
        boolean bEncontrado = false;
        int iIterations = 0;
        while (iIterations < iMaxIterations && !openList.isEmpty()) {
            iIterations++;
            nodo = openList.remove(0); // Pillamos el 1er nodo (la lista ya estara ordenada)
            if (nodo.x == endPoint.x && nodo.y == endPoint.y && nodo.z == endPoint.z) { // Hemos encontrado el destino
                bEncontrado = true;
                break;
            } else {
				// Miramos cuadros adyacentes y los metemos en la lista abierta

                // Arriba / Abajo
                if (Terrain.canGoUp(nodo)) {
                    checkNewNode(nodo, openList, nodo.x, nodo.y, nodo.z - 1);
                }
                if (Terrain.canGoDown(nodo)) {
                    checkNewNode(nodo, openList, nodo.x, nodo.y, nodo.z + 1);
                }

                // Horizontales y verticales
                checkNewNode(nodo, openList, nodo.x - 1, nodo.y, nodo.z);
                checkNewNode(nodo, openList, nodo.x, nodo.y - 1, nodo.z);
                checkNewNode(nodo, openList, nodo.x, nodo.y + 1, nodo.z);
                checkNewNode(nodo, openList, nodo.x + 1, nodo.y, nodo.z);

				// Diagonales
                // Es mas rapido (casi el doble) si no las miramos, aunque el camino puede quedar ortopedico
                // Aunque eso se soluciona con la optimizacion de caminos que hacemos una vez encontrado el mismo
                // El problema es que a veces no encuentra camino debido a que llega a un final de caminillo donde no hay diagonales posibles
                // pero por la mitad de ese camino si que habia.
                // De momento las miramos siempre
                checkNewNode(nodo, openList, nodo.x - 1, nodo.y - 1, nodo.z);
                checkNewNode(nodo, openList, nodo.x - 1, nodo.y + 1, nodo.z);
                checkNewNode(nodo, openList, nodo.x + 1, nodo.y - 1, nodo.z);
                checkNewNode(nodo, openList, nodo.x + 1, nodo.y + 1, nodo.z);

                if (nodo.z > 0 && World.getCell(nodo.x, nodo.y, nodo.z - 1).isMined()) {
                    // Subir colinillas
                    checkNewNode(nodo, openList, nodo.x - 1, nodo.y, nodo.z - 1);
                    checkNewNode(nodo, openList, nodo.x, nodo.y - 1, nodo.z - 1);
                    checkNewNode(nodo, openList, nodo.x, nodo.y + 1, nodo.z - 1);
                    checkNewNode(nodo, openList, nodo.x + 1, nodo.y, nodo.z - 1);
                    checkNewNode(nodo, openList, nodo.x - 1, nodo.y - 1, nodo.z - 1);
                    checkNewNode(nodo, openList, nodo.x - 1, nodo.y + 1, nodo.z - 1);
                    checkNewNode(nodo, openList, nodo.x + 1, nodo.y - 1, nodo.z - 1);
                    checkNewNode(nodo, openList, nodo.x + 1, nodo.y + 1, nodo.z - 1);
                }

                if (nodo.z < (World.MAP_DEPTH - 1)) {
                    // Bajar colinillas
                    checkNewNodeDown(nodo, openList, nodo.x - 1, nodo.y, nodo.z + 1);
                    checkNewNodeDown(nodo, openList, nodo.x, nodo.y - 1, nodo.z + 1);
                    checkNewNodeDown(nodo, openList, nodo.x, nodo.y + 1, nodo.z + 1);
                    checkNewNodeDown(nodo, openList, nodo.x + 1, nodo.y, nodo.z + 1);
                    checkNewNodeDown(nodo, openList, nodo.x - 1, nodo.y - 1, nodo.z + 1);
                    checkNewNodeDown(nodo, openList, nodo.x - 1, nodo.y + 1, nodo.z + 1);
                    checkNewNodeDown(nodo, openList, nodo.x + 1, nodo.y - 1, nodo.z + 1);
                    checkNewNodeDown(nodo, openList, nodo.x + 1, nodo.y + 1, nodo.z + 1);
                }

                // Metemos el nodo actual en la lista cerrada
                getClosedList()[nodo.x][nodo.y][nodo.z] = true;
            }
        }
        // Devolvemos el resultado
        if (bEncontrado) {
            ArrayList<Point3DShort> alReturn = new ArrayList<Point3DShort>();
            if (nodo != null) {
                if (isIniToEnd()) {
                    // De inicial a final, hay que girar el resultado
                    while (nodo.getPadre() != null) {
                        alReturn.add(0, (Point3DShort) nodo);
                        nodo = nodo.getPadre();
                    }
                } else {
                    // De final a inicial, el resultado es ok
                    while (nodo.getPadre() != null) {
                        alReturn.add((Point3DShort) nodo);
                        nodo = nodo.getPadre();
                    }
                    alReturn.add((Point3DShort) nodo);
                }

                // Los nodos que queden en la lista abierta se van para el pool
                while (openList.size() > 0) {
                    AStarNodo.returnToPool(openList.remove(0));
                }
            }

			//setClosedList (null);
			// Tenemos 1 camino posible a destino, optimizamos la ruta para que no sea ortopedica
            //preSmoothPath (alReturn, getLivingEntityType ());
            preSmoothPath(alReturn);
            setPath(alReturn);

            setFinished(true);
        } else {
            if (openList.isEmpty()) { // No ha encontrado camino
                //setClosedList (null);
                setFinished(true);
//			} else {
                // Aun no ha acabado
            }
        }

        return iIterations;
    }

    /**
     * Metodo interno del A*
     */
    private boolean checkNewNode(AStarNodo currentNode, AStarBinaryHeap alListaAbierta, int x, int y, int z) {
        // Miro la closedList[0][0].length, no fuera que el levels discovered creciera mientras busca un camino y decidiera buscar por ahi
        if (z >= getClosedList()[0][0].length) {
            return false;
        }

        if (isAllowed(x, y, z)) {
            if (!getClosedList()[x][y][z]) {
                AStarNodo nodoAux = AStarNodo.getPoolInstance(x, y, z, currentNode, endPoint.x, endPoint.y, endPoint.z);

                int iIndex = openListIndexes[x][y][z];
                if (iIndex != -1) {
                    // Miramos si este nodo es mejor que el que ya tenemos
                    if (alListaAbierta.get(iIndex).getG() > currentNode.getG()) {
                        AStarNodo.returnToPool(alListaAbierta.set(iIndex, nodoAux));
                        return true;
                    }
                } else {
                    alListaAbierta.add(nodoAux);
                    return true;
                }

                // Si el nodo Aux no se ha usado lo devolvemos al pool
                AStarNodo.returnToPool(nodoAux);
            }
        }

        return false;
    }

    private boolean checkNewNodeDown(AStarNodo currentNode, AStarBinaryHeap alListaAbierta, int x, int y, int z) {
        // Miro la closedList[0][0].length, no fuera que el levels discovered creciera mientras busca un camino y decidiera buscar por ahi
        if (z >= getClosedList()[0][0].length) {
            return false;
        }

        if (isAllowed(x, y, z) && World.getCell(x, y, z - 1).isMined()) {
            if (!getClosedList()[x][y][z]) {
                AStarNodo nodoAux = AStarNodo.getPoolInstance(x, y, z, currentNode, endPoint.x, endPoint.y, endPoint.z);

                int iIndex = openListIndexes[x][y][z];
                if (iIndex != -1) {
                    // Miramos si este nodo es mejor que el que ya tenemos
                    if (alListaAbierta.get(iIndex).getG() > currentNode.getG()) {
                        AStarNodo.returnToPool(alListaAbierta.set(iIndex, nodoAux));
                        return true;
                    }
                } else {
                    alListaAbierta.add(nodoAux);
                    return true;
                }

                // Si el nodo Aux no se ha usado lo devolvemos al pool
                AStarNodo.returnToPool(nodoAux);
            }
        }
        return false;
    }

    /**
     * Metodo interno del A*
     */
    private boolean isAllowed(int x, int y, int z) {
        if (x < 0 || x >= World.MAP_WIDTH || y < 0 || y >= World.MAP_HEIGHT || z < 0 || z > Game.getWorld().getNumFloorsDiscovered()) {
            return false;
        }
//		return World.getCell (currentNode.x, currentNode.y, currentNode.z).getAstarZoneID () == World.getCell (x, y, z).getAstarZoneID ();

        return LivingEntity.isCellAllowed(x, y, z);
    }

    /**
     * Busca bresenham lines en el camino y reemplaza (y elminina) los puntos
     * que toque con la misma Esto evita diagonales raras al andar El metodo
     * tiene que ser rapido para no perder performance
     *
     * @param alPath
     */
    public static void preSmoothPath(ArrayList<Point3DShort> alPath) {//, int livingType) {
        if (alPath == null || alPath.size() < 3) {
            return;
        }

		// Empieza la fiesta
        // Miramos la primera Z, porque este metodo no funciona para Z distintas
        int startIndex = 0;
        short z = alPath.get(0).z;

        // Miramos donde cambia la Z
        int iIndex = 1;
        while (iIndex < alPath.size()) {
            if (alPath.get(iIndex).z != z) {
				// Z diferente, bingo
                // Llamamos al optimizador real (en caso de paths > 2)
                if ((startIndex + 3) < iIndex) {
                    iIndex = smoothPath(alPath, startIndex, (iIndex - 1), z); //, livingType);
//				} else {
                    // Path demasiado corto, no hay que tocar el indice
                }

                // Obtenemos la nueva Z
                startIndex = iIndex;
                z = alPath.get(startIndex).z;
            } else {
                // Misma Z
                iIndex++;
            }
        }

        // Al salir de aqui smootheamos el ultimo tramo
        smoothPath(alPath, startIndex, alPath.size() - 1, z); //, livingType);
    }

    /**
     * Busca bresenham lines en el camino y reemplaza (y elminina) los puntos
     * que toque con la misma Esto evita diagonales raras al andar El metodo
     * tiene que ser rapido para no perder performance
     *
     * @param alPath Camino
     * @param startIndex Inicio
     * @param endIndex Final
     *
     * @return indica el nuevo startIndex
     */
    private static int smoothPath(ArrayList<Point3DShort> alPath, int startIndex, int endIndex, short z) { //, int livingType) {
        int returnNewIndex = endIndex;

        int stepSize = (int) Math.sqrt(Math.sqrt(endIndex - startIndex));
        if (stepSize < 2) {
            stepSize = 2;
        }

        ArrayList<Point3DShort> alBresenham;
        int iIndexStart = startIndex;
        int iIndexEnd = endIndex;

        while ((iIndexStart + 3) < returnNewIndex) {
            alBresenham = Utils.bresenhamLine(alPath.get(iIndexStart), alPath.get(iIndexEnd), z); //, livingType);
            if (alBresenham != null && alBresenham.size() > 2 && countWalk(alPath, iIndexStart, alBresenham.size()) >= countWalk(alBresenham, 0, alBresenham.size())) {
//			if (alBresenham != null && alBresenham.size () > 2) {
                // Linea recta, sustituimos
                // Miramos el tamano de lo que tenemos en el path
                int subPathSize = (iIndexEnd - iIndexStart);
                if (alBresenham.size() <= (subPathSize + 1)) {
                    // Linea del mismo tamano o mas corta, primero sustituimos el path

                    for (int j = 0; j < alBresenham.size(); j++) {
                        alPath.get(iIndexStart + j).setPoint(alBresenham.get(j).x, alBresenham.get(j).y, alBresenham.get(j).z);
                    }

                    if (alBresenham.size() < subPathSize) {
                        // Linea mas corta, hay que borrar lo sobrante del path
                        while (subPathSize > alBresenham.size()) {
                            alPath.remove(iIndexStart + alBresenham.size());
                            returnNewIndex--;
                            subPathSize--;
                        }
                    }

                    // Cambiamos el index al final de lo copiado
                    iIndexStart = iIndexStart + alBresenham.size();

                    // Reseteamos el end index
                    iIndexEnd = returnNewIndex;
                } else {
                    // Lo encontrado es mas largo.... ?? raro
                    iIndexEnd -= stepSize;
                    if (iIndexEnd <= iIndexStart) {
                        iIndexEnd = returnNewIndex;
                        iIndexStart += stepSize;
                    }
                }

            } else {
                // Sin path o path demasiado corto
                iIndexEnd -= stepSize;
                if (iIndexEnd <= iIndexStart) {
                    iIndexEnd = returnNewIndex;
                    iIndexStart += stepSize;
                }
            }

            // Limpiamos la lista Bresenham
            Point3DShort.returnToPool(alBresenham);
        }

        return (returnNewIndex + 1);
    }

    /**
     * Cuenta el valor de walk para recorrer un camino
     *
     * @param list
     * @return
     */
    private static int countWalk(ArrayList<Point3DShort> list, int iIni, int iNumCells) {
        int iReturn = 0;
        for (int i = 0; i < iNumCells; i++) {
            if (list.size() > (iIni + i)) {
                iReturn += Cell.getCellWalkNeeded(list.get(iIni + i));
            } else {
                iReturn += 100;
            }
        }

        return iReturn;
    }
}
