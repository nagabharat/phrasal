from tm.models import SourceTxt,TargetTxt,UserConf,TranslationStats,ExperimentModule
from django.contrib import admin

# View these tables in the admin interface
admin.site.register(SourceTxt)
admin.site.register(TargetTxt)
admin.site.register(UserConf)
admin.site.register(TranslationStats)
admin.site.register(ExperimentModule)
