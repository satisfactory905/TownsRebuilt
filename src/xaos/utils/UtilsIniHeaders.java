package xaos.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

public class UtilsIniHeaders {

    private static final long serialVersionUID = 8132377885335964294L;

    private static HashMap<String, Integer> hmIniHeaders = new HashMap<String, Integer>();
    private static ArrayList<String> alStringIniHeaders = new ArrayList<String>();
    private static int MAX_INI_HEADER = 0;

    public static int getIntIniHeader(String iniHeader) {
        Integer intReturn = hmIniHeaders.get(iniHeader);
        if (intReturn == null) {
            hmIniHeaders.put(iniHeader, MAX_INI_HEADER);
            alStringIniHeaders.add(iniHeader);

            intReturn = MAX_INI_HEADER;
            MAX_INI_HEADER++;
        }

        return intReturn;
    }

    public static String getStringIniHeader(int iniHeader) {
        return alStringIniHeaders.get(iniHeader);
    }

    public static boolean contains(int[] aiInts, int iValue) {
        if (aiInts == null) {
            return false;
        }

        for (int i = 0; i < aiInts.length; i++) {
            if (aiInts[i] == iValue) {
                return true;
            }
        }

        return false;
    }

    public static boolean contains(String[] aStrings, int iValue) {
        if (aStrings == null) {
            return false;
        }

        for (int i = 0; i < aStrings.length; i++) {
            if (getIntIniHeader(aStrings[i]) == iValue) {
                return true;
            }
        }

        return false;
    }

    public static int[] getIntsArray(ArrayList<String> alStrings) {
        if (alStrings == null) {
            return null;
        }

        int[] aiInts = new int[alStrings.size()];
        for (int i = 0; i < aiInts.length; i++) {
            aiInts[i] = getIntIniHeader(alStrings.get(i));
        }

        return aiInts;
    }

    public static int[] getIntsArray(String[] aStrings) {
        if (aStrings == null) {
            return null;
        }

        int[] aiInts = new int[aStrings.length];
        for (int i = 0; i < aiInts.length; i++) {
            aiInts[i] = getIntIniHeader(aStrings[i]);
        }

        return aiInts;
    }

    public static int[] getIntsArray(String sString) {
        if (sString == null) {
            return null;
        }

        StringTokenizer tokenizer = new StringTokenizer(sString, ",");
        if (!tokenizer.hasMoreTokens()) {
            return null;
        }

        ArrayList<Integer> alInts = new ArrayList<Integer>();
        while (tokenizer.hasMoreTokens()) {
            alInts.add(getIntIniHeader(tokenizer.nextToken()));
        }

        int[] aiInts = new int[alInts.size()];
        for (int i = 0; i < aiInts.length; i++) {
            aiInts[i] = alInts.get(i);
        }
        return aiInts;
    }

    public static ArrayList<int[]> getArrayIntsArray(ArrayList<String[]> alArray) {
        if (alArray == null) {
            return null;
        }

        ArrayList<int[]> alReturn = new ArrayList<int[]>(alArray.size());
        for (int i = 0; i < alArray.size(); i++) {
            int[] aints = new int[alArray.get(i).length];
            for (int j = 0; j < alArray.get(i).length; j++) {
                aints[j] = getIntIniHeader(alArray.get(i)[j]);
            }
            alReturn.add(aints);
        }

        return alReturn;
    }

    public static ArrayList<String[]> getArrayStringsArray(ArrayList<int[]> alArray) {
        if (alArray == null) {
            return null;
        }

        ArrayList<String[]> alReturn = new ArrayList<String[]>(alArray.size());
        for (int i = 0; i < alArray.size(); i++) {
            String[] aints = new String[alArray.get(i).length];
            for (int j = 0; j < alArray.get(i).length; j++) {
                aints[j] = new String(getStringIniHeader(alArray.get(i)[j]));
            }
            alReturn.add(aints);
        }

        return alReturn;
    }

    public static ArrayList<String> getArrayStrings(ArrayList<Integer> alArray) {
        if (alArray == null) {
            return null;
        }

        ArrayList<String> alReturn = new ArrayList<String>(alArray.size());
        for (int j = 0; j < alArray.size(); j++) {
            alReturn.add(new String(getStringIniHeader(alArray.get(j))));
        }

        return alReturn;
    }

    /**
     * Indica si el ID pasado esta en la lista, y devuelve la posicion. -1 en
     * caso de no encontrarlo
     *
     * @return
     */
    public static int contains(ArrayList<int[]> alList, int iValue) {
        if (alList == null || alList.size() == 0) {
            return -1;
        }

        for (int i = 0; i < alList.size(); i++) {
            if (contains(alList.get(i), iValue)) {
                return i;
            }
        }

        return -1;
    }
}
