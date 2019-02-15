package namespace

import akka.actor.AbstractActor
import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.japi.pf.ReceiveBuilder
import akka.pattern.Patterns.*
import java.time.Duration


fun main() {

    val actorSystem = ActorSystem.create("part1")
//    val actorRef = actorSystem.actorOf(Props.create(LoggingActor::class.java), "Logger")
    val summer = actorSystem.actorOf(SumActor.props, "Summer")
    val overlord = actorSystem.actorOf(Overlord.props, "Overlord")

    summer.tell(SumActor.Sum(1, 3), ActorRef.noSender())

    println(getActorTree(actorSystem))
    Thread.sleep(1000L)
    ask(overlord, Overlord.GetActors(), Duration.ofSeconds(1))
            .thenAccept {
                if(it is Overlord.Actors) {
                    println("Actors:")
                    it.actors.forEach { actor ->
                        println("\tactor: $actor ")
                    }
                }
            }.toCompletableFuture().join()

    summer.tell(SumActor.Shutdown(), ActorRef.noSender())
    Thread.sleep(10000L)
    ask(overlord, Overlord.GetActors(), Duration.ofSeconds(1))
            .thenAccept {
                if(it is Overlord.Actors) {
                    println("Actors:")
                    it.actors.forEach { actor ->
                        println("\tactor: $actor ")
                    }
                }
            }.toCompletableFuture().join()

    println(getActorTree(actorSystem))

    Thread.sleep(20000)
    actorSystem.terminate()
}

private fun getActorTree(actorSystem: ActorSystem): String {
    val method = actorSystem::class.java.getDeclaredMethod("printTree")
    method.isAccessible = true
    return method.invoke(actorSystem) as String
}

class LoggingActor : AbstractLoggingActor() {
    companion object {
        val props: Props = Props.create(LoggingActor::class.java)
    }

    data class LogMessage(val msg: String)

    override fun createReceive(): Receive {
        context.actorSelection("/user/Overlord").tell(Overlord.Register(self), self)
        return ReceiveBuilder()
                .match(LogMessage::class.java) {
                    log().info(it.msg)
                }
                .build()
    }


    override fun postStop() {
        context.actorSelection("/user/Overlord").tell(Overlord.UnRegister(self), self)
    }
}

class SumActor : AbstractActor() {
    companion object {
        val props: Props = Props.create(SumActor::class.java)
    }

    data class Sum(val left: Int, val right: Int)
    class Shutdown

    override fun createReceive(): Receive {
        context.actorSelection("/user/Overlord").tell(Overlord.Register(self), self)
        return ReceiveBuilder()
                .match(Sum::class.java) {
                    context.actorOf(LoggingActor.props, "Logger").tell(LoggingActor.LogMessage("sum: ${it.left + it.right}"), self)
                }
                .match(Shutdown::class.java) {
                    println("Shutting down")
                    context.stop(self)
                }
                .build()
    }

    override fun postStop() {
        context.actorSelection("/user/Overlord").tell(Overlord.UnRegister(self), self)
    }
}

class Overlord : AbstractActor() {
    companion object {
        val props: Props = Props.create(Overlord::class.java)
    }

    class GetActors
    data class Actors(val actors: Set<ActorRef>)
    data class Register(val ref: ActorRef)
    data class UnRegister(val ref: ActorRef)

    private val actors = mutableSetOf<ActorRef>(self)

    override fun createReceive(): Receive {
        val logger = context.actorOf(LoggingActor.props, "logger")
        return ReceiveBuilder()
                .match(Register::class.java) {
                    actors += it.ref
                    logger.tell(LoggingActor.LogMessage("Added actor ${it.ref}"), self)
                }
                .match(UnRegister::class.java) {
                    actors -= it.ref
                }
                .match(GetActors::class.java) {
                    sender.tell(Actors(actors), self)
                }
                .build()
    }
}
