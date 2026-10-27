package com.sentinel.audit.model;

/** O que auditar quando a origem é uma pasta local. */
public enum LocalSnapshot {
    /** O último commit da branch: repetível, com SHA e histórico. É o padrão. */
    COMMIT,
    /** A pasta como está agora, inclusive o que ainda não foi commitado. Sem SHA nem histórico. */
    TREE
}
