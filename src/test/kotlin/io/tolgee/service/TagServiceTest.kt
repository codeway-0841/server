/*
 * Copyright (c) 2020. Tolgee
 */

package io.tolgee.service

import io.tolgee.AbstractSpringTest
import io.tolgee.assertions.Assertions.assertThat
import io.tolgee.development.testDataBuilder.data.TagsTestData
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.testng.annotations.Test

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TagServiceTest : AbstractSpringTest() {

  @Test
  fun `deletes just unused tags on batch delete`() {
    val tagsTestData = TagsTestData()
    testDataService.saveTestData(tagsTestData.root)
    tagService.deleteAllByKeyIdIn(listOf(tagsTestData.existingTagKey.id))
    entityManager.flush()
    entityManager.clear()
    assertThat(tagService.find(tagsTestData.existingTag2.id)).isNotNull
    assertThat(tagService.find(tagsTestData.existingTag.id)).isNull()
  }

  @Test
  fun `deletes many keys fast enough`() {
    val tagsTestData = TagsTestData()
    tagsTestData.generateVeryLotOfData()
    testDataService.saveTestData(tagsTestData.root)
    entityManager.flush()
    entityManager.clear()
    val start = System.currentTimeMillis()
    val ids = tagsTestData.root.data.projects.flatMap { it.data.keys.map { it.self.id } }
    tagService.deleteAllByKeyIdIn(ids)
    entityManager.flush()
    val time = System.currentTimeMillis() - start
    assertThat(time).isLessThan(10000)
  }
}
