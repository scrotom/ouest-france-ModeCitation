/*
 * Nom         : CustomAppException.java
 *
 * Description : Classe permettant la gestion des exceptions personnalisées pour l'application.
 *
 * Date        : 07/06/2024
 *
 */

package com.ouestfrance.modecitation.Exception;

public class CustomAppException extends Exception {

    public CustomAppException() {
        super();
    }

    public CustomAppException(String message) {
        super(message);
    }

    public CustomAppException(String message, Throwable cause) {
        super(message, cause);
    }

    public CustomAppException(Throwable cause) {
        super(cause);
    }
}
