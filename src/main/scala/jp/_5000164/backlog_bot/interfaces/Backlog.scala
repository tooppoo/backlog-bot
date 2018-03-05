package jp._5000164.backlog_bot.interfaces

import java.util.Date

import com.nulabinc.backlog4j.conf.{BacklogConfigure, BacklogJpConfigure}
import com.nulabinc.backlog4j.internal.json.activities.{IssueCommentedContent, IssueCreatedContent, IssueUpdatedContent}
import com.nulabinc.backlog4j.{Activity, BacklogClient, BacklogClientFactory}
import jp._5000164.backlog_bot.domain.Message

import scala.collection.JavaConverters._

class Backlog {
  val spaceId = sys.env("BACKLOG_SPACE_ID")
  val apiKey = sys.env("BACKLOG_API_KEY")
  val projectKey = sys.env("BACKLOG_PROJECT_KEY")
  val configure: BacklogConfigure = new BacklogJpConfigure(spaceId).apiKey(apiKey)
  val client: BacklogClient = new BacklogClientFactory(configure).newClient()

  def fetchMessages(lastExecutedAt: Date): List[Option[Message]] = {
    val project = client.getProject(projectKey)
    val activities = client.getProjectActivities(project.getId)
    activities.asScala.filter(_.getCreated after lastExecutedAt).map {
      case activity if activity.getType == Activity.Type.IssueCreated =>
        val content = activity.getContent.asInstanceOf[IssueCreatedContent]
        val issue = client.getIssue(content.getId)
        Some(Message.build(spaceId, projectKey, activity, content, issue))
      case activity if activity.getType == Activity.Type.IssueUpdated =>
        val content = activity.getContent.asInstanceOf[IssueUpdatedContent]
        val comment = client.getIssueComment(content.getId, content.getComment.getId)
        Some(Message.build(spaceId, projectKey, activity, content, comment))
      case activity if activity.getType == Activity.Type.IssueCommented =>
        val content = activity.getContent.asInstanceOf[IssueCommentedContent]
        val comment = client.getIssueComment(content.getId, content.getComment.getId)
        Some(Message.build(spaceId, projectKey, activity, content, comment))
      case _ => None
    }.toList.reverse
  }
}
