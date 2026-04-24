package xaos.utils;

import java.util.ArrayList;

import xaos.main.World;
import xaos.tiles.entities.living.LivingEntity;

public final class AStarQueue implements Runnable {

    // Numero de iteraciones a tratar en cada pasada del bucle principal
    public static int NUM_ITERATIONS = 2048 * 2;

    private static ArrayList<AStarQueueItem> requests;
    private static ArrayList<AStarQueueItem> finishedRequests;

    private static boolean exit;
    private static boolean exitOK = true;
    private static boolean pause;
    private static boolean pauseOK = false;

    public AStarQueue() {
        exitOK = true;
        pauseOK = false;
        requests = new ArrayList<AStarQueueItem>();
        finishedRequests = new ArrayList<AStarQueueItem>();
    }

    /**
     * Anade una peticion de busqueda de camino A*
     *
     * @param item peticion
     */
    public static void addRequest(AStarQueueItem item) {
        // Borramos lo que tenga
        removeItem(item.getLivingEntityID());

        // Anadimos la peticion
        synchronized (requests) {
            requests.add(item);
        }
    }

    /**
     *
     * @param iLivingEntityID
     */
    private static void removeItem(int iLivingEntityID) {
        AStarQueueItem itemAux;

		// Primero los requests (importante)
        // Eliminaremos de paso los nulls que encontremos
        synchronized (requests) {
            int i = requests.size() - 1;

            while (i >= 0) {
                itemAux = requests.get(i);

                if (itemAux == null) {
                    requests.remove(i);
                } else if (itemAux.getLivingEntityID() == iLivingEntityID) {
                    // Esta en la cola, borramos lo que hay
                    requests.remove(i);
                    break;
                }

                i--;
            }
        }

        // Despues los finished requests
        synchronized (finishedRequests) {
            int i = finishedRequests.size() - 1;

            while (i >= 0) {
                itemAux = finishedRequests.get(i);
                if (itemAux == null) {
                    finishedRequests.remove(i);
                } else if (itemAux.getLivingEntityID() == iLivingEntityID) {
                    // Esta en la cola, borramos lo que hay
                    finishedRequests.remove(i);
                    break;
                }

                i--;
            }
        }
    }

    /**
     * Asignamos los caminos terminados a las living entities
     */
    public static void setFinishedPaths() {
        ArrayList<Point3DShort> path;
        AStarQueueItem asqi;
        synchronized (finishedRequests) {
            int iIndex = finishedRequests.size() - 1;
            LivingEntity le;
            while (iIndex >= 0) {
                asqi = finishedRequests.remove(iIndex);

                if (asqi != null) {
                    le = World.getLivingEntityByID(asqi.getLivingEntityID());
                    if (le != null) {
                        path = asqi.getPath();

                        if (path != null) {
                            // Eliminamos celdas del path si es requerido
                            int iCellsToRemove = asqi.getNumCellsToRemove();
                            while (iCellsToRemove > 0) {
                                if (path.size() > 0) {
                                    path.remove(path.size() - 1);
                                } else {
                                    iCellsToRemove = 0;
                                }

                                iCellsToRemove--;
                            }
                        }

                        // Le ponemos el camino a la living y la marcamos para que no espere mas
                        le.setPath(path);
                        le.setWaitingForPath(false);

						// Si el path es null es que no ha encontrado camino, en ese caso levantamos el flag de "recheckASZID" en World para que haga un rechequeo
                        //World.setRecheckASZID (true);
                    }
                }
                iIndex--;
            }
        }
    }

    public void run() {
        AStarQueueItem item;
        int iIterations;

        exit = false;
        exitOK = false;
        pause = false;
        pauseOK = false;

        while (!exit) {
            try {
                // Espera X milisegundos
                Thread.sleep(256);
            } catch (InterruptedException e) {
            }

            if (!pause) {
                pauseOK = false; // Por si acaso
                // Tratamos los A* en cola
                iIterations = NUM_ITERATIONS;
//				long l = System.currentTimeMillis ();
//				int finished = 0;
                synchronized (requests) {
                    while (iIterations > 0 && !requests.isEmpty()) {
                        item = requests.get(0);
                        iIterations -= item.search(iIterations);

                        if (item.isFinished()) {
//							finished++;
                            // Busqueda terminada, la ponemos en la cola de acabados
                            AStarQueueItem qItem = requests.remove(0);
                            synchronized (finishedRequests) {
                                finishedRequests.add(qItem);
                            }

                            // Si era un path de siege, ponemos delante un no-siege (si existen)
                            int iNonSiegeIndex = -1;
                            for (int i = 0; i < requests.size(); i++) {
                                if (!requests.get(i).isSiege()) {
                                    iNonSiegeIndex = i;
                                    break;
                                }
                            }
                            if (iNonSiegeIndex != -1) {
                                requests.add(0, requests.remove(iNonSiegeIndex));
                            }
                        }
                    }
                }
//				if (iIterations != NUM_ITERATIONS) {
//					long ll = System.currentTimeMillis () - l;
//					if (ll > 300) {
//						System.out.println ("Recheck (" + (ll) + ") -> " + iIterations + " [" + finished + "]");
//					}
//				}
            } else {
                pauseOK = true;
            }
        }

        exitOK = true;
    }

    public static void exit() {
        exit = true;
    }

    public static boolean isExitOK() {
        return exitOK;
    }

    public static void pause() {
        pause = true;
    }

    public static void resume() {
        pause = false;
        pauseOK = false;
    }

    public static boolean isPauseOK() {
        return pauseOK;
    }
}
