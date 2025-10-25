package com.usic.SistemasActivosFijosUAP.interoperabilidad.test;

public class DbfIntrospect {
    public static void main(String[] args) throws Exception {
        Class<?> dbfField = Class.forName("com.linuxense.javadbf.DBFField");
        System.out.println("Constructores de DBFField:");
        for (var c : dbfField.getDeclaredConstructors()) {
            System.out.println("  " + c.toString());
        }

        Class<?> dbfReader = Class.forName("com.linuxense.javadbf.DBFReader");
        System.out.println("\nConstructores de DBFReader:");
        for (var c : dbfReader.getDeclaredConstructors()) {
            System.out.println("  " + c.toString());
        }

        Class<?> dbfWriter = Class.forName("com.linuxense.javadbf.DBFWriter");
        System.out.println("\nConstructores de DBFWriter:");
        for (var c : dbfWriter.getDeclaredConstructors()) {
            System.out.println("  " + c.toString());
        }

        System.out.println("\nMétodos de DBFField relevantes:");
        for (var m : dbfField.getDeclaredMethods()) {
            if (m.getName().contains("set") || m.getName().contains("get") || m.getName().toLowerCase().contains("type")) {
                System.out.println("  " + m.toString());
            }
        }
    }
}
