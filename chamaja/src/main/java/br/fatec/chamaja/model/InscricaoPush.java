package br.fatec.chamaja.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Inscrição de Web Push do aparelho do cliente, ligada a uma senha. Some junto com a senha
 * (ON DELETE CASCADE): nada do aparelho do cliente sobrevive ao fim do dia.
 */
@Entity
@Table(name = "inscricao_push", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inscricao_push_endpoint", columnNames = "endpoint"),
        @UniqueConstraint(name = "uk_inscricao_push_senha", columnNames = "senha_id")
})
public class InscricaoPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "senha_id")
    private Senha senha;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    /** Chave pública do navegador (P-256, base64url). */
    @Column(nullable = false, length = 255)
    private String p256dh;

    /** Segredo de autenticação do navegador (base64url). */
    @Column(nullable = false, length = 100)
    private String auth;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm = Instant.now();

    protected InscricaoPush() {
    }

    public InscricaoPush(Senha senha, String endpoint, String p256dh, String auth) {
        this.senha = senha;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public void atualizarChaves(String p256dh, String auth) {
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() {
        return id;
    }

    public Senha getSenha() {
        return senha;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
