package Transaction

object UniqueId {

  var id = 0

  def getId(): Int = {
  id
}

  def updateId() = {
  id = id + 1
}
}
