package writes.nestedMutations.alreadyConverted

import org.scalatest.{FlatSpec, Matchers}
import util.ConnectorCapability.JoinRelationLinksCapability
import util._

class NestedDisconnectMutationInsideUpdateSpec extends FlatSpec with Matchers with ApiSpecBase with SchemaBaseV11 {
  override def runOnlyForCapabilities = Set(JoinRelationLinksCapability)

  "a P1 to C1 relation " should "be disconnectable through a nested mutation by id" in {
    schemaWithRelation(onParent = ChildOpt, onChild = ParentOpt).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val res = server
        .query(
          s"""mutation {
          |  createParent(data: {
          |    p: "p1", p_1: "p", p_2: "1"
          |    childOpt: {
          |      create: {
          |        c: "c1"
          |        c_1: "c_1"
          |        c_2: "c_2"
          |      }
          |    }
          |  }){
          |    ${t.parent.selection}
          |  }
          |}""",
          project
        )

      val parentIdentifier = t.parent.where(res, "data.createParent")

      val res2 = server.query(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    p: "p2"
         |    childOpt: {disconnect: true}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res2.toString should be("""{"data":{"updateParent":{"childOpt":null}}}""")

    }
  }

  "a P1 to C1  relation with the child and the parent without a relation" should "not be disconnectable through a nested mutation by id" in {
    schemaWithRelation(onParent = ChildOpt, onChild = ParentOpt).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      server.query(
        s"""mutation {
          |  createChild(data: {
          |    c: "c1"
          |    c_1: "c_1"
          |    c_2: "c_2"
          |  })
          |  {
          |    ${t.child.selection}
          |  }
          |}""",
        project
      )

      val parentResult = server
        .query(
          s"""mutation {
          |  createParent(data: {p: "p1", p_1: "p", p_2: "1"})
          |  {
          |    ${t.parent.selection}
          |  }
          |}""",
          project
        )

      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      val res = server.queryThatMustFail(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    p: "p2"
         |    childOpt: {disconnect: true}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project,
        errorCode = 2017,
        errorContains = """Error occurred during query execution:\nInterpretationError(\"Error for binding \\'1\\': RecordsNotConnected { relation_name: \\\"ChildToParent\\\", parent_name: \\\"Parent\\\", child_name: \\\"Child\\\" }"""
      )

    }
  }

  "a PM to C1!  relation with the child already in a relation" should "not be disconnectable through a nested mutation by unique" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: { c: "c1", c_1: "c", c_2: "1" }
        |    }
        |  }){
        |    ${t.parent.selection}
        |    childrenOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")
      val childIdentifier  = t.child.whereMulti(parentResult, "data.createParent.childrenOpt")(0)

      server.queryThatMustFail(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parentIdentifier
         |    data:{
         |      childrenOpt: {disconnect: $childIdentifier }
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project,
        errorCode = 2014,
        errorContains = """Error in query graph construction: RelationViolation(RelationViolation { relation_name: \"ChildToParent\", model_a_name: \"Child\", model_b_name: \"Parent\" """
      )

    }
  }

  "a P1 to C1!  relation with the child and the parent already in a relation" should "should error in a nested mutation by unique" in {
    schemaWithRelation(onParent = ChildOpt, onChild = ParentReq).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childOpt: {
        |      create: { c: "c1", c_1: "c", c_2: "1" }
        |    }
        |  }){
        |    ${t.parent.selection}
        |    childOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      server.queryThatMustFail(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childOpt: {disconnect: true}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project,
        errorCode = 2014,
        errorContains = """The change you are trying to make would violate the required relation 'ChildToParent' between Child and Parent""",
      )

    }
  }

  "a PM to C1 relation with the child already in a relation" should "be disconnectable through a nested mutation by unique" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentOpt).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
          |  createParent(data: {
          |    p: "p1", p_1: "p", p_2: "1"
          |    childrenOpt: {
          |      create: [
          |        { c: "c1", c_1: "c", c_2: "1" },
          |        { c: "c2", c_1: "c", c_2: "2" }
          |      ]
          |    }
          |  }){
          |    ${t.parent.selection}
          |    childrenOpt{
          |       ${t.child.selection}
          |    }
          |  }
          |}""",
        project
      )

      val parentIdentifier = t.parent.where(parentResult, "data.createParent")
      val secondChild      = t.child.whereMulti(parentResult, "data.createParent.childrenOpt")(1)

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childrenOpt: {disconnect: [$secondChild]}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"}]}}}""")

    }
  }

  "a P1 to CM  relation with the child already in a relation" should "be disconnectable through a nested mutation by unique" in {
    schemaWithRelation(onParent = ChildOpt, onChild = ParentList).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childOpt: {
        |      create: {
        |        c: "c1"
        |        c_1: "c_1"
        |        c_2: "c_2"
        |      }
        |    }
        |  }){
        |    ${t.parent.selection}
        |    childOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )
      val parentIdentifier = t.parent.where(parentResult, "data.createParent")

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |    where: $parentIdentifier
         |    data:{
         |    childOpt: {disconnect: true}
         |  }){
         |    childOpt{
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childOpt":null}}}""")

      server.query(s"""query{children{c, parentsOpt{p}}}""", project).toString should be("""{"data":{"children":[{"c":"c1","parentsOpt":[]}]}}""")

    }
  }

  "a PM to CM  relation with the children already in a relation" should "be disconnectable through a nested mutation by unique" taggedAs (IgnoreMongo) in {
    // since this assumes transactionality, test ist split below
    schemaWithRelation(onParent = ChildList, onChild = ParentList).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: [
        |        { c: "c1", c_1: "c", c_2: "1" },
        |        { c: "c2", c_1: "c", c_2: "2" }
        |      ]
        |    }
        |  }){
        |    ${t.parent.selection}
        |    childrenOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )

      val parentIdentifier = t.parent.where(parentResult, "data.createParent")
      val firstChild       = t.child.whereMulti(parentResult, "data.createParent.childrenOpt")(0)

      val otherParentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "otherParent", p_1: "otherParent_1", p_2: "otherParent_2"
        |    childrenOpt: {
        |      create: [
        |        { c: "otherChild", c_1: "otherChild_1", c_2: "otherChild_2" }
        |      ]
        |      connect: [$firstChild]
        |    }
        |  }){
        |    childrenOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )
      val otherChild = t.child.whereMulti(otherParentResult, "data.createParent.childrenOpt")(1)

      server.queryThatMustFail(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childrenOpt: {disconnect: [$firstChild, $otherChild]}
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
        project,
        errorCode = 2017,
        errorContains = """Error occurred during query execution:\nInterpretationError(\"Error for binding \\'1\\': RecordsNotConnected { relation_name: \\\"ChildToParent\\\", parent_name: \\\"Parent\\\", child_name: \\\"Child\\\" }"""
      )

      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childrenOpt: {disconnect: [$firstChild]}
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c2"}]}}}""")

      server.query(s"""query{child(where:{c:"c1"}){c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"child":{"c":"c1","parentsOpt":[{"p":"otherParent"}]}}}""")

      server.query(s"""query{child(where:{c:"c2"}){c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"child":{"c":"c2","parentsOpt":[{"p":"p1"}]}}}""")

      server.query(s"""query{child(where:{c:"otherChild"}){c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"child":{"c":"otherChild","parentsOpt":[{"p":"otherParent"}]}}}""")

    }
  }

  "a PM to CM  relation with the children already in a relation" should "be disconnectable through a nested mutation by unique 2" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentList).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: [
        |        { c: "c1", c_1: "c", c_2: "1" },
        |        { c: "c2", c_1: "c", c_2: "2" }
        |      ]
        |    }
        |  }){
        |    ${t.parent.selection}
        |    childrenOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )

      val parentIdentifier = t.parent.where(parentResult, "data.createParent")
      val child1Identifier = t.child.whereMulti(parentResult, "data.createParent.childrenOpt")(0)

      val otherParentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "otherParent", p_1: "otherParent_1", p_2: "otherParent_2"
        |    childrenOpt: {
        |      create: [
        |        { c: "otherChild", c_1: "otherChild_1", c_2: "otherChild_2" }
        |      ]
        |      connect: [$child1Identifier]
        |    }
        |  }){
        |    childrenOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )
      val otherChild = t.child.whereMulti(otherParentResult, "data.createParent.childrenOpt")(1)

      server.queryThatMustFail(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childrenOpt: {disconnect: [$child1Identifier, $otherChild]}
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
        project,
        errorCode = 2017,
        errorContains = """Error occurred during query execution:\nInterpretationError(\"Error for binding \\'1\\': RecordsNotConnected { relation_name: \\\"ChildToParent\\\", parent_name: \\\"Parent\\\", child_name: \\\"Child\\\" }\")"""
      )
    }
  }

  "a PM to CM  relation with the children already in a relation" should "be disconnectable through a nested mutation by unique 3" in {
    schemaWithRelation(onParent = ChildList, onChild = ParentList).test { t =>
      val project = SchemaDsl.fromStringV11() {
        t.datamodel
      }
      database.setup(project)

      val parentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "p1", p_1: "p", p_2: "1"
        |    childrenOpt: {
        |      create: [
        |        { c: "c1", c_1: "c", c_2: "1" },
        |        { c: "c2", c_1: "c", c_2: "2" }
        |      ]
        |    }
        |  }){
        |    ${t.parent.selection}
        |    childrenOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )

      val parentIdentifier = t.parent.where(parentResult, "data.createParent")
      val child1Identifier = t.child.whereMulti(parentResult, "data.createParent.childrenOpt")(0)
      val otherParentResult = server.query(
        s"""mutation {
        |  createParent(data: {
        |    p: "otherParent", p_1: "otherParent_1", p_2: "otherParent_2"
        |    childrenOpt: {
        |      create: [{ c: "otherChild", c_1: "otherChild_1", c_2: "otherChild_2" }]
        |      connect: [$child1Identifier]
        |    }
        |  }){
        |    childrenOpt{
        |       ${t.child.selection}
        |    }
        |  }
        |}""",
        project
      )

      val otherChild = t.child.whereMulti(otherParentResult, "data.createParent.childrenOpt")(1)
      val res = server.query(
        s"""
         |mutation {
         |  updateParent(
         |  where: $parentIdentifier
         |  data:{
         |    childrenOpt: {disconnect: [$child1Identifier]}
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """,
        project
      )

      res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c2"}]}}}""")

      server.query(s"""query{child(where:{c:"c1"}){c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"child":{"c":"c1","parentsOpt":[{"p":"otherParent"}]}}}""")

      server.query(s"""query{child(where:{c:"c2"}){c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"child":{"c":"c2","parentsOpt":[{"p":"p1"}]}}}""")

      server.query(s"""query{child(where:{c:"otherChild"}){c, parentsOpt{p}}}""", project).toString should be(
        """{"data":{"child":{"c":"otherChild","parentsOpt":[{"p":"otherParent"}]}}}""")

    }
  }

  // OTHER DATAMODELS

  "a one to many relation" should "be disconnectable by id through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() {
      s"""model Todo{
            id       String    @id @default(cuid())
            comments Comment[] $relationInlineDirective
        }

        model Comment{
            id String @id @default(cuid())
            text String?
            todo Todo?
        }"""
    }

    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1"}, {text: "comment2"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments { id }
        |  }
        |}""",
      project
    )

    val todoId     = createResult.pathAsString("data.createTodo.id")
    val comment1Id = createResult.pathAsString("data.createTodo.comments.[0].id")
    val comment2Id = createResult.pathAsString("data.createTodo.comments.[1].id")

    val result = server.query(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    data:{
         |      comments: {
         |        disconnect: [{id: "$comment1Id"}, {id: "$comment2Id"}]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.updateTodo.comments").toString, """[]""")
  }

  "a one to many relation" should "be disconnectable by any unique argument through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() {
      s"""model Todo {
              id       String    @id @default(cuid())
              comments Comment[] $relationInlineDirective
          }

          model Comment {
              id    String  @id @default(cuid())
              text  String?
              alias String  @unique
              todo  Todo?
          }"""
    }

    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1", alias: "alias1"}, {text: "comment2", alias: "alias2"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments { id }
        |  }
        |}""",
      project
    )
    val todoId = createResult.pathAsString("data.createTodo.id")

    val result = server.query(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      id: "$todoId"
         |    }
         |    data:{
         |      comments: {
         |        disconnect: [{alias: "alias1"}, {alias: "alias2"}]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.updateTodo.comments").toString, """[]""")
  }

  "a many to one relation" should "be disconnectable by id through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() {
      s"""model Todo{
              id       String    @id @default(cuid())
              comments Comment[] $relationInlineDirective
          }

          model Comment{
              id   String  @id @default(cuid())
              text String?
              todo Todo?
          }"""
    }

    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createTodo(
        |    data: {
        |      comments: {
        |        create: [{text: "comment1"}]
        |      }
        |    }
        |  ){
        |    id
        |    comments { id }
        |  }
        |}""",
      project
    )
    val todoId    = createResult.pathAsString("data.createTodo.id")
    val commentId = createResult.pathAsString("data.createTodo.comments.[0].id")

    val result = server.query(
      s"""
         |mutation {
         |  updateComment(
         |    where: {
         |      id: "$commentId"
         |    }
         |    data: {
         |      todo: {disconnect: true}
         |    }
         |  ){
         |    todo {
         |      id
         |    }
         |  }
         |}
      """,
      project
    )
    mustBeEqual(result.pathAsJsValue("data.updateComment").toString, """{"todo":null}""")
  }

  "a one to one relation" should "be disconnectable by id through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() {
      """model Note{
              id   String  @id @default(cuid())
              text String?
              todo Todo?   @relation(references: [id])
          }

          model Todo{
              id    String @id @default(cuid())
              title String
              note  Note?
          }"""
    }

    database.setup(project)

    val createResult = server.query(
      """mutation {
        |  createNote(
        |    data: {
        |      todo: {
        |        create: { title: "the title" }
        |      }
        |    }
        |  ){
        |    id
        |    todo { id }
        |  }
        |}""",
      project
    )
    val noteId = createResult.pathAsString("data.createNote.id")
    val todoId = createResult.pathAsString("data.createNote.todo.id")

    val result = server.query(
      s"""
         |mutation {
         |  updateNote(
         |    where: { id: "$noteId"}
         |    data: { todo: { disconnect: true } }
         |  ){
         |    todo { title }
         |  }
         |}
      """,
      project
    )
    mustBeEqual(result.pathAsJsValue("data.updateNote").toString, """{"todo":null}""")
  }

  "a one to many relation" should "be disconnectable by unique through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() {
      s"""model Todo{
              id       String    @id @default(cuid())
              title    String    @unique
              comments Comment[] $relationInlineDirective
          }

          model Comment{
              id   String  @id @default(cuid())
              text String? @unique
              todo Todo?
          }"""
    }

    database.setup(project)

    server.query("""mutation { createTodo(data: {title: "todo"}){ id } }""", project).pathAsString("data.createTodo.id")
    server.query("""mutation { createComment(data: {text: "comment1"}){ id } }""", project).pathAsString("data.createComment.id")
    server.query("""mutation { createComment(data: {text: "comment2"}){ id } }""", project).pathAsString("data.createComment.id")

    val result = server.query(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      title: "todo"
         |    }
         |    data:{
         |      comments: {
         |        connect: [{text: "comment1"}, {text: "comment2"}]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.updateTodo.comments").toString, """[{"text":"comment1"},{"text":"comment2"}]""")

    val result2 = server.query(
      s"""mutation {
         |  updateTodo(
         |    where: {
         |      title: "todo"
         |    }
         |    data:{
         |      comments: {
         |        disconnect: [{text: "comment2"}]
         |      }
         |    }
         |  ){
         |    comments {
         |      text
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result2.pathAsJsValue("data.updateTodo.comments").toString, """[{"text":"comment1"}]""")
  }

  "A PM CM self relation" should "be disconnectable by unique through a nested mutation" in {
    val project = SchemaDsl.fromStringV11() { s"""|
                                              |model User {
                                              |  id        String  @id @default(cuid())
                                              |  banned    Boolean @default(value: false)
                                              |  username  String  @unique
                                              |  password  String
                                              |  salt      String
                                              |  followers     User[]  @relation(name: "UserFollowers" $listInlineArgument)
                                              |  followersBack User[]  @relation(name: "UserFollowers")
                                              |  follows       User[]  @relation(name: "UserFollows" $listInlineArgument)
                                              |  followsBack   User[]  @relation(name: "UserFollows")
                                              |}""" }
    database.setup(project)

    server.query("""mutation { createUser(data: {username: "Paul", password: "1234", salt: "so salty"}){ id } }""", project)
    server.query("""mutation { createUser(data: {username: "Peter", password: "abcd", salt: "so salty"}){ id } }""", project)

    val result = server.query(
      s"""mutation {
         |  updateUser(
         |    where: {
         |      username: "Paul"
         |    }
         |    data:{
         |      follows: {
         |        connect: [{username: "Peter"}]
         |      }
         |    }
         |  ){
         |    username
         |    follows {
         |      username
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.updateUser.follows").toString, """[{"username":"Peter"}]""")

    val result2 = server.query(
      s"""mutation {
         |  updateUser(
         |    where: {
         |      username: "Paul"
         |    }
         |    data:{
         |      follows: {
         |        disconnect: [{username: "Peter"}]
         |      }
         |    }
         |  ){
         |    username
         |    follows {
         |      username
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result2.pathAsJsValue("data.updateUser.follows").toString, """[]""")
  }

  "A PM CM self relation" should "should throw a correct error for disconnect on invalid unique" in {
    val project = SchemaDsl.fromStringV11() { s"""|
                                              |model User {
                                              |  id        String  @id @default(cuid())
                                              |  banned    Boolean @default(value: false)
                                              |  username  String  @unique
                                              |  password  String
                                              |  salt      String
                                              |  followers     User[]  @relation(name: "UserFollowers" $listInlineArgument)
                                              |  followersBack User[]  @relation(name: "UserFollowers")
                                              |  follows       User[]  @relation(name: "UserFollows" $listInlineArgument)
                                              |  followsBack   User[]  @relation(name: "UserFollows")
                                              |}""" }
    database.setup(project)

    server.query("""mutation { createUser(data: {username: "Paul", password: "1234", salt: "so salty"}){ id } }""", project)
    server.query("""mutation { createUser(data: {username: "Peter", password: "abcd", salt: "so salty"}){ id } }""", project)
    server.query("""mutation { createUser(data: {username: "Anton", password: "abcd3", salt: "so salty"}){ id } }""", project)

    val result = server.query(
      s"""mutation {
         |  updateUser(
         |    where: {
         |      username: "Paul"
         |    }
         |    data:{
         |      follows: {
         |        connect: [{username: "Peter"}]
         |      }
         |    }
         |  ){
         |    username
         |    follows {
         |      username
         |    }
         |  }
         |}
      """,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.updateUser.follows").toString, """[{"username":"Peter"}]""")

    server.queryThatMustFail(
      s"""mutation {
         |  updateUser(
         |    where: {
         |      username: "Paul"
         |    }
         |    data:{
         |      follows: {
         |        disconnect: [{username: "Anton"}]
         |      }
         |    }
         |  ){
         |    username
         |    follows {
         |      username
         |    }
         |  }
         |}
      """,
      project,
      errorCode = 2017,
      errorContains = """Error occurred during query execution:\nInterpretationError(\"Error for binding \\'1\\': RecordsNotConnected { relation_name: \\\"UserFollows\\\", parent_name: \\\"User\\\", child_name: \\\"User\\\" }"""
//      errorContains =
//        "The relation UserFollows has no Node for the model User with value `Paul` for username connected to a Node for the model User with value `Anton` for username"
    )
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path" in {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String @id @default(cuid())
                                             |  nameTop String @unique
                                             |  middles Middle[] $relationInlineDirective
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String @id @default(cuid())
                                             |  nameMiddle String @unique
                                             |  tops       Top[]
                                             |  bottoms    Bottom[] $relationInlineDirective
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String   @id @default(cuid())
                                             |  nameBottom String   @unique
                                             |  middles    Middle[]
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottoms: {disconnect: [{nameBottom: "the bottom"}]
         |              }
         |       }}]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: id_ASC){
         |      nameMiddle
         |      bottoms (orderBy: id_ASC){
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"the second bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")

    server.query("query{bottoms(orderBy: id_ASC){nameBottom}}", project).toString should be(
      """{"data":{"bottoms":[{"nameBottom":"the bottom"},{"nameBottom":"the second bottom"},{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only node edges on the path and there are no backrelations" in {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String   @id @default(cuid())
                                             |  nameTop String   @unique
                                             |  middles Middle[] $relationInlineDirective
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String   @id @default(cuid())
                                             |  nameMiddle String   @unique
                                             |  bottoms    Bottom[] $relationInlineDirective
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String @id @default(cuid())
                                             |  nameBottom String @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottoms: {
        |            create: [{ nameBottom: "the bottom"}, { nameBottom: "the second bottom"}]
        |          }
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottoms: {
        |            create: [{nameBottom: "the third bottom"},{nameBottom: "the fourth bottom"}]
        |          }
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottoms: {disconnect: [{nameBottom: "the bottom"}]
         |              }
         |       }}]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: id_ASC){
         |      nameMiddle
         |      bottoms (orderBy: id_ASC){
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottoms":[{"nameBottom":"the second bottom"}]},{"nameMiddle":"the second middle","bottoms":[{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}]}}}""")

    server.query("query{bottoms(orderBy: id_ASC){nameBottom}}", project).toString should be(
      """{"data":{"bottoms":[{"nameBottom":"the bottom"},{"nameBottom":"the second bottom"},{"nameBottom":"the third bottom"},{"nameBottom":"the fourth bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path " in {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String   @id @default(cuid())
                                             |  nameTop String   @unique
                                             |  middles Middle[] $relationInlineDirective
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  tops       Top[]
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String  @id @default(cuid())
                                             |  nameBottom String  @unique
                                             |  middle     Middle?
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middles: {
        |      create:[
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {create: { nameBottom: "the bottom"}}
        |        },
        |        {
        |          nameMiddle: "the second middle"
        |          bottom: {create: { nameBottom: "the second bottom"}}
        |        }
        |     ]
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middles: {
         |        update: [{
         |              where: {nameMiddle: "the middle"},
         |              data:{  nameMiddle: "updated middle"
         |                      bottom: {disconnect: true}
         |              }
         |              }]
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middles (orderBy: id_ASC) {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middles":[{"nameMiddle":"updated middle","bottom":null},{"nameMiddle":"the second middle","bottom":{"nameBottom":"the second bottom"}}]}}}""")

    server.query("query{bottoms{nameBottom}}", project).toString should be(
      """{"data":{"bottoms":[{"nameBottom":"the bottom"},{"nameBottom":"the second bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are model and node edges on the path  and back relations are missing and node edges follow model edges" in {
    val project = SchemaDsl.fromStringV11() { s"""model Top {
                                             |  id      String  @id @default(cuid())
                                             |  nameTop String  @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String  @id @default(cuid())
                                             |  nameBottom String  @unique
                                             |  below      Below[] $relationInlineDirective
                                             |}
                                             |
                                             |model Below {
                                             |  id        String @id @default(cuid())
                                             |  nameBelow String @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation a {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: { nameBottom: "the bottom"
        |            below: {
        |            create: [{ nameBelow: "below"}, { nameBelow: "second below"}]}
        |        }}}
        |        }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""mutation b {
         |  updateTop(
         |    where: {nameTop: "the top"},
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |               nameMiddle: "updated middle"
         |               bottom: {
         |                update: {
         |                  nameBottom: "updated bottom"
         |                  below: { disconnect: {nameBelow: "below"}
         |
         |          }
         |                }
         |          }
         |       }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |        below (orderBy: id_ASC){
         |           nameBelow
         |        }
         |
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be(
      """{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":{"nameBottom":"updated bottom","below":[{"nameBelow":"second below"}]}}}}}""")

    server.query("query{belows(orderBy: id_ASC){nameBelow}}", project).toString should be(
      """{"data":{"belows":[{"nameBelow":"below"},{"nameBelow":"second below"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only model edges on the path" in {
    val project = SchemaDsl.fromStringV11() { """model Top {
                                             |  id      String  @id @default(cuid())
                                             |  nameTop String  @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  top        Top?
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String  @id @default(cuid())
                                             |  middle     Middle?
                                             |  nameBottom String  @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: {
        |              nameBottom: "the bottom"
        |            }
        |          }
        |        }
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""
         |mutation  {
         |  updateTop(
         |    where: {
         |      nameTop: "the top"
         |    }
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |              nameMiddle: "updated middle"
         |              bottom: {disconnect: true}
         |      }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be("""{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":null}}}}""")

    server.query("query{bottoms{nameBottom}}", project).toString should be("""{"data":{"bottoms":[{"nameBottom":"the bottom"}]}}""")
  }

  "a deeply nested mutation" should "execute all levels of the mutation if there are only model edges on the path and there are no backrelations" in {
    val project = SchemaDsl.fromStringV11() { """model Top {
                                             |  id      String  @id @default(cuid())
                                             |  nameTop String  @unique
                                             |  middle  Middle? @relation(references: [id])
                                             |}
                                             |
                                             |model Middle {
                                             |  id         String  @id @default(cuid())
                                             |  nameMiddle String  @unique
                                             |  bottom     Bottom? @relation(references: [id])
                                             |}
                                             |
                                             |model Bottom {
                                             |  id         String @id @default(cuid())
                                             |  nameBottom String @unique
                                             |}""" }
    database.setup(project)

    val createMutation =
      """
        |mutation  {
        |  createTop(data: {
        |    nameTop: "the top",
        |    middle: {
        |      create:
        |        {
        |          nameMiddle: "the middle"
        |          bottom: {
        |            create: {
        |              nameBottom: "the bottom"
        |            }
        |          }
        |        }
        |    }
        |  }) {id}
        |}
      """

    server.query(createMutation, project)

    val updateMutation =
      s"""
         |mutation  {
         |  updateTop(
         |    where: {
         |      nameTop: "the top"
         |    }
         |    data: {
         |      nameTop: "updated top",
         |      middle: {
         |        update: {
         |              nameMiddle: "updated middle"
         |              bottom: {disconnect: true}
         |      }
         |     }
         |   }
         |  ) {
         |    nameTop
         |    middle {
         |      nameMiddle
         |      bottom {
         |        nameBottom
         |      }
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be("""{"data":{"updateTop":{"nameTop":"updated top","middle":{"nameMiddle":"updated middle","bottom":null}}}}""")

    server.query("query{bottoms{nameBottom}}", project).toString should be("""{"data":{"bottoms":[{"nameBottom":"the bottom"}]}}""")
  }

  "Nested disconnect on self relations" should "only disconnect the specified nodes" taggedAs IgnoreMongo in {
    val project = SchemaDsl.fromStringV11() { s"""model User {
                                             |  id        String @id @default(cuid())
                                             |  name      String @unique
                                             |  follower  User[] @relation(name: "UserFollow" $listInlineArgument)
                                             |  following User[] @relation(name: "UserFollow")
                                             |}""" }
    database.setup(project)

    server.query("""mutation  {createUser(data: {name: "X"}) {id}}""", project)
    server.query("""mutation  {createUser(data: {name: "Y"}) {id}}""", project)
    server.query("""mutation  {createUser(data: {name: "Z"}) {id}}""", project)

    val updateMutation =
      s""" mutation {
         |  updateUser(data:{
         |    following: {
         |      connect: [{ name: "Y" }, { name: "Z"}]
         |    }
         |  },where:{
         |    name:"X"
         |  }) {
         |    name
         |    following{
         |      name
         |    }
         |    follower{
         |      name
         |    }
         |  }
         |}
      """

    val result = server.query(updateMutation, project)

    result.toString should be("""{"data":{"updateUser":{"name":"X","following":[{"name":"Y"},{"name":"Z"}],"follower":[]}}}""")

    val check = server.query("""query{users{name, following{name}}}""", project)

    check.toString should be(
      """{"data":{"users":[{"name":"X","following":[{"name":"Y"},{"name":"Z"}]},{"name":"Y","following":[]},{"name":"Z","following":[]}]}}""")

    val disconnectMutation =
      s""" mutation {
         |  updateUser(data:{
         |    follower: {
         |      disconnect: [{ name: "X" }]
         |    }
         |  },where:{
         |    name:"Y"
         |  }) {
         |    name
         |    following{
         |      name
         |    }
         |  }
         |}
      """

    val result2 = server.query(disconnectMutation, project)

    result2.toString should be("""{"data":{"updateUser":{"name":"Y","following":[]}}}""")

    val result3 = server.query("""query{users{name, following{name}}}""", project)

    result3.toString should be("""{"data":{"users":[{"name":"X","following":[{"name":"Z"}]},{"name":"Y","following":[]},{"name":"Z","following":[]}]}}""")
  }

}
