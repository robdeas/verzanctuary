package tech.robd.verzanctuary
/**
 * [🧩 File Info]
 * path=src/test/kotlin/tech/robd/verzanctuary/AllVerzanctuaryTests.kt
 * description=The main testsuite for IDE visibility.
 * editable=true
 * license=apache
 * [/🧩 File Info]
 */

/**
 * Copyright 2025 Rob Deas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.junit.platform.suite.api.IncludePackages
import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

@Suite
@SelectPackages("tech.robd.verzanctuary")
@IncludePackages("tech.robd.verzanctuary")
class AllVerzanctuaryTests