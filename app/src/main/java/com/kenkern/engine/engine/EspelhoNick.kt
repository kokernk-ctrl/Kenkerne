package com.kenkern.engine.engine

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene

/**
 * S3 (v0.1.24) — INSPETOR AO VIVO EM PLAY: espelho do estado EDITÁVEL de um
 * Nick (transform + classes + flags + nome) aplicado na CÓPIA de Play.
 *
 * CAUSA RAIZ que este objeto corrige: em Play o viewport (e a física) usam a
 * CÓPIA da cena (KenkernSurfaceView.sceneForFrame), mas os comandos do
 * Inspetor escrevem SÓ na cena do editor — a edição nunca aparecia na tela
 * durante o Play. Depois de cada comando de campo/transform, o ViewModel
 * captura [de] (na thread da UI, com COPIAS dos valores mutáveis) e o GL
 * thread aplica [aplicar] na cópia de Play ANTES do próximo frame — o mesmo
 * frame em que o usuário soltou o campo.
 *
 * PURO (sem GL, sem android): JVM-testável. Estruturas criadas/excluídas
 * DURANTE o Play não são espelhadas (a cópia nasce no Play) — [aplicar]
 * devolve false quando o Nick não existe na cópia, sem crash.
 */
class EspelhoNick private constructor(
    val id: ReferenceId,
    val name: String?,
    val active: Boolean,
    val visible: Boolean,
    val px: Float, val py: Float, val pz: Float,
    val qx: Float, val qy: Float, val qz: Float, val qw: Float,
    val sx: Float, val sy: Float, val sz: Float,
    /** typeName → campos (valores COPIADOS: NickValue é mutável). */
    val classes: Map<String, Map<String, NickValue>>
) {

    companion object {

        /** Captura o estado editável do Nick (COPIAS — segura entre threads). */
        fun de(nick: Nick): EspelhoNick {
            val classesCopiadas = LinkedHashMap<String, Map<String, NickValue>>(nick.classes.size)
            for (instancia in nick.classes) {
                val campos = LinkedHashMap<String, NickValue>(instancia.fields.size)
                for ((nome, valor) in instancia.fields) campos[nome] = valor.copyValue()
                classesCopiadas[instancia.typeName] = campos
            }
            val t = nick.transform
            return EspelhoNick(
                nick.id, nick.name, nick.active, nick.visible,
                t.position.x, t.position.y, t.position.z,
                t.rotation.x, t.rotation.y, t.rotation.z, t.rotation.w,
                t.scale.x, t.scale.y, t.scale.z,
                classesCopiadas
            )
        }

        /**
         * Aplica o espelho no Nick de MESMO id da cena (a cópia de Play).
         * Escreve nos campos EXISTENTES (classe ausente na cópia é pulada —
         * sem crash); devolve false se o Nick não está na cena.
         */
        fun aplicar(espelho: EspelhoNick, cena: Scene?): Boolean {
            val alvo = cena?.findNick(espelho.id) ?: return false
            alvo.name = espelho.name
            alvo.active = espelho.active
            alvo.visible = espelho.visible
            val t = alvo.transform
            t.position.set(espelho.px, espelho.py, espelho.pz)
            t.rotation.set(espelho.qx, espelho.qy, espelho.qz, espelho.qw)
            t.scale.set(espelho.sx, espelho.sy, espelho.sz)
            t.localMatrix.setTRS(t.position, t.rotation, t.scale)
            for ((typeName, campos) in espelho.classes) {
                val instancia: NickClassInstance = alvo.classes.firstOrNull { it.typeName == typeName } ?: continue
                for ((nome, valor) in campos) instancia.fields[nome] = valor.copyValue()
            }
            return true
        }
    }
}
