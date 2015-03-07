<?cs def:custom_masthead() ?>
<a name="top"></a>
<?cs if:!devsite ?><?cs # leave out the global header for devsite; it's in devsite template ?>
    <!-- Header -->
    <div id="header">
        <div class="wrap" id="header-wrap">
          <div class="col-3 logo">
          <a href="<?cs var:toroot ?>index.html">
            <img src="<?cs var:toroot ?>assets/images/dac_logo.png" width="123" height="25" alt="Android Developers" />
          </a>
          <div class="btn-quicknav" id="btn-quicknav">
          	<a href="#" class="arrow-inactive">Quicknav</a>
			      <a href="#" class="arrow-active">Quicknav</a>
          </div>
          </div>
            <ul class="nav-x col-9">
                <li class="design">
                  <a href="<?cs var:toroot ?>design/index.html"
                  zh-tw-lang="設計"
                  zh-cn-lang="设计"
                  ru-lang="Проектирование"
                  ko-lang="디자인"
                  ja-lang="設計"
                  es-lang="Diseñar"               
                  >Design</a></li>
                <li class="develop"><a href="<?cs var:toroot ?>develop/index.html"
                  zh-tw-lang="開發"
                  zh-cn-lang="开发"
                  ru-lang="Разработка"
                  ko-lang="개발"
                  ja-lang="開発"
                  es-lang="Desarrollar"               
                  >Develop</a></li>
                <li class="distribute last"><a href="<?cs var:toroot ?>distribute/index.html"
                  zh-tw-lang="發佈"
                  zh-cn-lang="分发"
                  ru-lang="Распространение"
                  ko-lang="배포"
                  ja-lang="配布"
                  es-lang="Distribuir"               
                  >Distribute</a></li>
            </ul>
            
            <!-- New Search -->
            <div class="menu-container">
            <div class="moremenu">
    <div id="more-btn"></div>
  </div>
  <div class="morehover" id="moremenu">
    <div class="top"></div>
    <div class="mid">
      <div class="header">Links</div>
      <ul>
        <li><a href="https://play.google.com/apps/publish/">Google Play Developer Console</a></li>
        <li><a href="http://android-developers.blogspot.com/">Android Developers Blog</a></li>
        <li><a href="<?cs var:toroot ?>about/index.html">About Android</a></li>
      </ul>
      <div class="header">Android Sites</div>
      <ul>
        <li><a href="http://www.android.com">Android.com</a></li>
        <li class="active"><a>Android Developers</a></li>
        <li><a href="http://source.android.com">Android Open Source Project</a></li>
      </ul>
      
      <?cs # Include language switcher only in online docs ?>
      <?cs if:android.whichdoc == "online" ?>
        <div class="header">Language</div>
          <div id="language" class="locales">
            <select name="language" onChange="changeLangPref(this.value, true)">
                <option value="en">English</option>
                <option value="es">Español</option>
                <option value="ja">日本語</option>
                <option value="ko">한국어</option>
                <option value="ru">Русский</option>
                <option value="zh-cn">中文 (中国)</option>
                <option value="zh-tw">中文 (台灣)</option>
            </select>
          </div>
        <script type="text/javascript">
          <!--
          loadLangPref();
            //-->
        </script>
      <?cs /if ?>
      <?cs # End of lang switcher ?>


      <br class="clearfix" />
    </div>
    <div class="bottom"></div>
  </div>
  <div class="search" id="search-container">
    <div class="search-inner">
      <div id="search-btn"></div>
      <div class="left"></div>
      <form onsubmit="return submit_search()">
        <input id="search_autocomplete" type="text" value="" autocomplete="off" name="q"
onfocus="search_focus_changed(this, true)" onblur="search_focus_changed(this, false)"
onkeydown="return search_changed(event, true, '<?cs var:toroot ?>')" 
onkeyup="return search_changed(event, false, '<?cs var:toroot ?>')" />
      </form>
      <div class="right"></div>
        <a class="close hide">close</a>
        <div class="left"></div>
        <div class="right"></div>
    </div>
  </div>

  <div class="search_filtered_wrapper reference">
    <div class="suggest-card reference no-display">
      <ul class="search_filtered">
      </ul>
    </div>
  </div>

  <div class="search_filtered_wrapper docs">
    <div class="suggest-card dummy no-display">&nbsp;</div>
    <div class="suggest-card develop no-display">
      <ul class="search_filtered">
      </ul>
      <div class="child-card guides no-display">
      </div>
      <div class="child-card training no-display">
      </div>
    </div>
    <div class="suggest-card design no-display">
      <ul class="search_filtered">
      </ul>
    </div>
    <div class="suggest-card distribute no-display">
      <ul class="search_filtered">
      </ul>
    </div>
  </div>

  </div>
  <!-- /New Search>
          
          
          <!-- Expanded quicknav -->
           <div id="quicknav" class="col-9">
                <ul>
                    <li class="design">
                      <ul>
                        <li><a href="<?cs var:toroot ?>design/index.html">Get Started</a></li>
                        <li><a href="<?cs var:toroot ?>design/style/index.html">Style</a></li>
                        <li><a href="<?cs var:toroot ?>design/patterns/index.html">Patterns</a></li>
                        <li><a href="<?cs var:toroot ?>design/building-blocks/index.html">Building Blocks</a></li>
                        <li><a href="<?cs var:toroot ?>design/downloads/index.html">Downloads</a></li>
                        <li><a href="<?cs var:toroot ?>design/videos/index.html">Videos</a></li>
                      </ul>
                    </li>
                    <li class="develop">
                      <ul>
                        <li><a href="<?cs var:toroot ?>training/index.html"
                          zh-tw-lang="訓練課程"
                          zh-cn-lang="培训"
                          ru-lang="Курсы"
                          ko-lang="교육"
                          ja-lang="トレーニング"
                          es-lang="Capacitación"               
                          >Training</a></li>
                        <li><a href="<?cs var:toroot ?>guide/components/index.html"
                          zh-tw-lang="API 指南"
                          zh-cn-lang="API 指南"
                          ru-lang="Руководства по API"
                          ko-lang="API 가이드"
                          ja-lang="API ガイド"
                          es-lang="Guías de la API"               
                          >API Guides</a></li>
                        <li><a href="<?cs var:toroot ?>reference/packages.html"
                          zh-tw-lang="參考資源"
                          zh-cn-lang="参考"
                          ru-lang="Справочник"
                          ko-lang="참조문서"
                          ja-lang="リファレンス"
                          es-lang="Referencia"               
                          >Reference</a></li>
                        <li><a href="<?cs var:toroot ?>tools/index.html"
                          zh-tw-lang="相關工具"
                          zh-cn-lang="工具"
                          ru-lang="Инструменты"
                          ko-lang="도구"
                          ja-lang="ツール"
                          es-lang="Herramientas"               
                          >Tools</a>
                          <ul><li><a href="<?cs var:toroot ?>sdk/index.html">Get the SDK</a></li></ul>
                        </li>
                        <li><a href="<?cs var:toroot ?>google/index.html">Google Services</a>
                        </li>
                        <?cs if:android.hasSamples ?>
                          <li><a href="<?cs var:toroot ?>samples/index.html">Samples</a>
                          </li>
                        <?cs /if ?>
                      </ul>
                    </li>
                    <li class="distribute last">
                      <ul>
                        <li><a href="<?cs var:toroot ?>distribute/index.html">Google Play</a></li>
                        <li><a href="<?cs var:toroot ?>distribute/googleplay/publish/index.html">Publishing</a></li>
                        <li><a href="<?cs var:toroot ?>distribute/googleplay/promote/index.html">Promoting</a></li>
                        <li><a href="<?cs var:toroot ?>distribute/googleplay/quality/index.html">App Quality</a></li>
                        <li><a href="<?cs var:toroot ?>distribute/googleplay/spotlight/index.html">Spotlight</a></li>
                        <li><a href="<?cs var:toroot ?>distribute/open.html">Open Distribution</a></li>
                      </ul>
                    </li>
                </ul>
          </div>
          <!-- /Expanded quicknav -->
        </div>
    </div>
    <!-- /Header -->
    
    
  <div id="searchResults" class="wrap" style="display:none;">
          <h2 id="searchTitle">Results</h2>
          <div id="leftSearchControl" class="search-control">Loading...</div>
  </div>
    
    
  <?cs if:training || guide || reference || tools || develop || google || samples ?>
    <!-- Secondary x-nav -->
    <div id="nav-x">
        <div class="wrap">
            <ul class="nav-x col-9 develop" style="width:100%">
                <li class="training"><a href="<?cs var:toroot ?>training/index.html"
                  zh-tw-lang="訓練課程"
                  zh-cn-lang="培训"
                  ru-lang="Курсы"
                  ko-lang="교육"
                  ja-lang="トレーニング"
                  es-lang="Capacitación"               
                  >Training</a></li>
                <li class="guide"><a href="<?cs var:toroot ?>guide/components/index.html"
                  zh-tw-lang="API 指南"
                  zh-cn-lang="API 指南"
                  ru-lang="Руководства по API"
                  ko-lang="API 가이드"
                  ja-lang="API ガイド"
                  es-lang="Guías de la API"               
                  >API Guides</a></li>
                <li class="reference"><a href="<?cs var:toroot ?>reference/packages.html"
                  zh-tw-lang="參考資源"
                  zh-cn-lang="参考"
                  ru-lang="Справочник"
                  ko-lang="참조문서"
                  ja-lang="リファレンス"
                  es-lang="Referencia"               
                  >Reference</a></li>
                <li class="tools"><a href="<?cs var:toroot ?>tools/index.html"
                  zh-tw-lang="相關工具"
                  zh-cn-lang="工具"
                  ru-lang="Инструменты"
                  ko-lang="도구"
                  ja-lang="ツール"
                  es-lang="Herramientas"
                  >Tools</a></li>
                <li class="google"><a href="<?cs var:toroot ?>google/index.html"
                  >Google Services</a>
                </li>
                <?cs if:android.hasSamples ?>
                  <li class="samples"><a href="<?cs var:toroot ?>samples/index.html"
                    >Samples</a>
                  </li>
                <?cs /if ?>
            </ul>
        </div>
        
    </div>
    <!-- /Sendondary x-nav -->
  <?cs /if ?>

<?cs /if ?>
<?cs # end if/else !devsite ?>

  <?cs 
/def ?>
