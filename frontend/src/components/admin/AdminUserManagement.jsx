export default function AdminUserManagement({
    usuariosOperativos,
    loadingUsuarios,
    usuariosExpandido,
    setUsuariosExpandido,
    guardandoUsuarioId,
    actualizarCampoUsuario,
    guardarUsuarioOperativo,
    styles
}) {
    return (
        <div style={styles.usuariosCard}>
            <div style={styles.seccionHeader}>
                <h2 style={{ ...styles.usuariosTitulo, marginBottom: 0 }}>Usuarios Operativos</h2>
                <button
                    style={styles.btnToggleSeccion}
                    onClick={() => setUsuariosExpandido((prev) => !prev)}
                >
                    {usuariosExpandido ? 'Ocultar' : 'Mostrar'}
                </button>
            </div>

            {usuariosExpandido && (
                <>
                    {loadingUsuarios ? (
                        <p>Cargando usuarios...</p>
                    ) : usuariosOperativos.length === 0 ? (
                        <p>No hay usuarios operativos disponibles.</p>
                    ) : (
                        <div style={styles.usuariosGrid}>
                            {usuariosOperativos.map((usuario) => (
                                <div key={usuario.id} style={styles.usuarioItem}>
                                    <div style={styles.usuarioFila}>
                                        <div>
                                            <label style={styles.usuarioLabel}>Rol</label>
                                            <input style={styles.usuarioInput} value={usuario.rol} readOnly />
                                        </div>
                                        <div>
                                            <label style={styles.usuarioLabel}>Nombre Completo</label>
                                            <input
                                                style={styles.usuarioInput}
                                                value={usuario.editNombre}
                                                onChange={(e) => actualizarCampoUsuario(usuario.id, 'editNombre', e.target.value)}
                                            />
                                        </div>
                                        <div>
                                            <label style={styles.usuarioLabel}>Username</label>
                                            <input
                                                style={styles.usuarioInput}
                                                value={usuario.editUsername}
                                                onChange={(e) => actualizarCampoUsuario(usuario.id, 'editUsername', e.target.value)}
                                            />
                                        </div>
                                        <div>
                                            <label style={styles.usuarioLabel}>Correo</label>
                                            <input
                                                style={styles.usuarioInput}
                                                type="email"
                                                value={usuario.editEmail}
                                                onChange={(e) => actualizarCampoUsuario(usuario.id, 'editEmail', e.target.value)}
                                            />
                                        </div>
                                        <div>
                                            <button
                                                style={{ ...styles.btnGuardarUsuario, ...(guardandoUsuarioId === usuario.id ? styles.btnGuardarUsuarioDisabled : {}) }}
                                                onClick={() => guardarUsuarioOperativo(usuario)}
                                                disabled={guardandoUsuarioId === usuario.id}
                                            >
                                                {guardandoUsuarioId === usuario.id ? 'Guardando...' : 'Guardar'}
                                            </button>
                                        </div>
                                    </div>
                                    <div style={styles.usuarioMeta}>Estado: {usuario.activo ? 'Activo' : 'Inactivo'}</div>
                                </div>
                            ))}
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
