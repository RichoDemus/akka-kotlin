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

//    actorSystem.log().info("Sending Hello Kotlin")
//    actorRef.tell("Hello Kotlin", ActorRef.noSender())
//    actorRef.tell(PoisonPill.getInstance(), ActorRef.noSender())

    summer.tell(SumActor.Sum(1, 3), ActorRef.noSender())

//    val workDone = actorSystem.whenTerminated()
//
//    do {
//        println("Waiting for the process to finish")
//        Thread.sleep(60000L)
//    } while (!workDone.isCompleted)

    println(getActorTree(actorSystem))
    Thread.sleep(1000L)
    ask(overlord, Overlord.GetActors(), Duration.ofSeconds(1))
            .thenAccept {
                if(it is Overlord.Actors) {
                    println("actors: $it")
                }
            }.toCompletableFuture().join()

    summer.tell(SumActor.Shutdown(), ActorRef.noSender())
    Thread.sleep(10000L)
    ask(overlord, Overlord.GetActors(), Duration.ofSeconds(1))
            .thenAccept {
                println("actors: $it")
            }

    println(getActorTree(actorSystem))

    Thread.sleep(20000)
    actorSystem.terminate()

}

private fun getActorTree(actorSystem: ActorSystem): String {
    val method = actorSystem::class.java.getDeclaredMethod("printTree")
    method.isAccessible = true
    val r = method.invoke(actorSystem)
    return r as String
}

fun printTree(it: List<ActorRef>): String {
//    it.first().
    return ""

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
        println("Logger stoped")
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

}

class Overlord : AbstractActor() {
    companion object {
        val props: Props = Props.create(Overlord::class.java)
    }

    class GetActors
    data class Actors(val actors: List<ActorRef>)
    data class Register(val ref: ActorRef)

    private val actors = mutableListOf<ActorRef>()

    override fun createReceive(): Receive {
        val logger = context.actorOf(LoggingActor.props)
        return ReceiveBuilder()
                .match(Register::class.java) {
                    actors += it.ref
                    logger.tell(LoggingActor.LogMessage("Added actor ${it.ref}"), self)
                }
                .match(GetActors::class.java) {
                    sender.tell(Actors(actors), self)
                }
                .build()
    }
}
