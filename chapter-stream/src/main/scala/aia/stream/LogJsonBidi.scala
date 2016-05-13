package aia.stream

import java.nio.file.{ Files, Path }
import java.io.File
import java.time.ZonedDateTime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{ Success, Failure }

import akka.Done
import akka.actor._
import akka.util.ByteString

import akka.stream.{ ActorAttributes, ActorMaterializer, IOResult }
import akka.stream.io.JsonFraming
import akka.stream.scaladsl.{ FileIO, BidiFlow, Flow, Framing, Keep, Sink, Source }

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import spray.json._

object LogJsonBidi extends EventMarshalling {
  def textInFlow(maxLine: Int) = {
    Framing.delimiter(ByteString("\n"), maxLine)
    .map(_.decodeString("UTF8"))
    .map(LogStreamProcessor.parseLineEx)
  }

  def jsonInFlow(maxJsonObject: Int) = {
    JsonFraming.json(maxJsonObject) 
      .map(_.decodeString("UTF8").parseJson.convertTo[Event])
  }

  val jsonOutFlow = Flow[Event].map { event => 
    ByteString(event.toJson.compactPrint)
  }

  val textOutFlow = Flow[Event].map{ event => 
    ByteString(LogStreamProcessor.logLine(event))
  }

  def logToJson(maxLine: Int) = {
    BidiFlow.fromFlows(textInFlow(maxLine), jsonOutFlow)
  }

  def jsonToLog(maxJsonObject: Int) = {
    BidiFlow.fromFlows(jsonInFlow(maxJsonObject), textOutFlow)
  }

  def logToJsonFlow(maxLine: Int) = {
    logToJson(maxLine).join(Flow[Event])
  }

  def jsonToLogFlow(maxJsonObject: Int) = {
    jsonToLog(maxJsonObject).join(Flow[Event])
  }
}