package xaos.utils;

import java.util.ArrayList;

public final class AStarBinaryHeap extends ArrayList<AStarNodo> {

    private static final long serialVersionUID = -3351822150023612005L;
//	public final static boolean BINARY_HEAP = true;

    public AStarBinaryHeap() {
        super();
    }

    public boolean add(AStarNodo nodo) {
//		if (!BINARY_HEAP) {
//			return super.add (nodo);
//		}

        super.add(nodo); // Lo metemos al final
        AStarQueueItem.openListIndexes[nodo.x][nodo.y][nodo.z] = (short) (size() - 1);
        subeNodo(size() - 1);
        return true;
    }

    public AStarNodo set(int iIndex, AStarNodo nodo) {
//		if (!BINARY_HEAP) {
//			return super.set (iIndex, nodo);
//		}

        // Aqui habra que hacer un remove del actual y un add del nuevo
        AStarNodo nodoReturn = remove(iIndex);
        add(nodo);

        return nodoReturn; // Lo devolvemos pq se usa para devolverse al pool
    }

    public AStarNodo remove(int iPosition) {
//		if (!BINARY_HEAP) {
//			return super.remove (iPosition);
//		}

        // Si es el ultimo elemento lo quitamos tal cual
        if (iPosition + 1 == size()) {
            AStarNodo nodo = super.remove(iPosition);
            AStarQueueItem.openListIndexes[nodo.x][nodo.y][nodo.z] = -1;
            return nodo;
        }

        // En otro caso pillamos el elemento a eliminar
        AStarNodo nodo = super.get(iPosition);

        if (size() > 1) {
            // Pillamos el ultimo y lo metemos en el hueco (Cuidado!! Usamos los metodos de super)
            AStarNodo aux = super.remove(size() - 1);
            super.set(iPosition, aux);
            AStarQueueItem.openListIndexes[aux.x][aux.y][aux.z] = (short) iPosition;
            AStarQueueItem.openListIndexes[nodo.x][nodo.y][nodo.z] = -1;

			// Miramos si hay que subirlo o bajarlo
            // Si su padre es mayor es que toca subirlo
            if (get((iPosition - 1) / 2).getF() > aux.getF()) {
                subeNodo(iPosition);
            } else {
                bajaNodo(iPosition);
            }

        } else {
            // Solo quedaba ese elemento, lo eliminamos
            AStarNodo aux = super.remove(0);
            AStarQueueItem.openListIndexes[aux.x][aux.y][aux.z] = -1;
            //super.clear ();
        }

        return nodo;
    }

    /**
     * Baja un nodo hasta su posicion
     *
     * @param iPosition
     */
    private void bajaNodo(int iPosition) {
        // Comparamos si el elemento es mayor que sus hijos
        if (size() > 1) {
            AStarNodo aux = get(iPosition);

            int iIndex = iPosition;
            int iIndexHijos = iIndex * 2 + 1;
            if (iIndexHijos < size()) {
                AStarNodo hijo1 = get(iIndexHijos);
                AStarNodo hijo2 = (iIndexHijos + 1) >= size() ? hijo1 : get(iIndexHijos + 1);
                while (iIndexHijos < size() && (aux.getF() > hijo1.getF() || aux.getF() > hijo2.getF())) {
                    // Swap (OJO! Usamos el super.set)
                    if (hijo1.getF() <= hijo2.getF()) {
                        super.set(iIndex, hijo1);
                        super.set(iIndexHijos, aux);
                        AStarQueueItem.openListIndexes[hijo1.x][hijo1.y][hijo1.z] = (short) iIndex;
                        AStarQueueItem.openListIndexes[aux.x][aux.y][aux.z] = (short) iIndexHijos;
                        iIndex = iIndexHijos;
                    } else {
                        super.set(iIndex, hijo2);
                        super.set(iIndexHijos + 1, aux);
                        AStarQueueItem.openListIndexes[hijo2.x][hijo2.y][hijo2.z] = (short) iIndex;
                        AStarQueueItem.openListIndexes[aux.x][aux.y][aux.z] = (short) (iIndexHijos + 1);
                        iIndex = iIndexHijos + 1;
                    }

                    // Actualizamos indices
                    iIndexHijos = iIndex * 2 + 1;
                    if (iIndexHijos < size()) {
                        hijo1 = get(iIndexHijos);
                        hijo2 = (iIndexHijos + 1) >= size() ? hijo1 : get(iIndexHijos + 1);
                    }
                }
            }
        }
    }

    /**
     * Sube un nodo hasta su posicion
     *
     * @param iPosition
     */
    private void subeNodo(int iPosition) {
        if (iPosition > 0) {
            AStarNodo nodo = get(iPosition);

            // Miramos si este elemento es menor que el de arriba (su padre)
            int iIndex = iPosition + 1;
            int iIndexPadre = iIndex / 2 - 1;;
            AStarNodo aux = get(iIndexPadre);
            while (iIndexPadre >= 0 && aux.getF() > nodo.getF()) {
                // Swap (OJO!! Usamos el set del super)
                super.set(iIndexPadre, nodo);
                super.set(iIndex - 1, aux);
                AStarQueueItem.openListIndexes[nodo.x][nodo.y][nodo.z] = (short) iIndexPadre;
                AStarQueueItem.openListIndexes[aux.x][aux.y][aux.z] = (short) (iIndex - 1);

                // Nueva iteracion
                iIndex = iIndexPadre + 1;
                iIndexPadre = iIndex / 2 - 1;
                if (iIndexPadre >= 0) {
                    aux = get(iIndexPadre);
                }
            }
        }
    }

//	public boolean estaOK () {
//		for (int i = 0; i < size (); i++) {
//			if (i*2+1 < size ()) {
//				if (get(i).getF () <= get (i*2+1).getF ()) {
//					if (i*2+2 < size ()) {
//						if (get(i).getF () > get (i*2+2).getF ()) {
//							return false;
//						}
//					}
//				} else {
//					return false;
//				}
//			}
//		}
//
//		return true;
//	}
}
