package io.redspace.ironsspellbooks.datafix.fixers;

import io.redspace.ironsspellbooks.capabilities.spell.SpellData;
import io.redspace.ironsspellbooks.datafix.DataFixerElement;
import io.redspace.ironsspellbooks.datafix.DataFixerHelpers;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public class FixIsbSpell extends DataFixerElement {
    @Override
    public List<String> preScanValuesToMatch() {
        return List.of(SpellData.ISB_SPELL);
    }

    @Override
    public boolean runFixer(CompoundTag tag) {
        if (tag != null) {
            var spellTag = (CompoundTag) tag.get(SpellData.ISB_SPELL);
            if (spellTag != null) {
                if (spellTag.contains(SpellData.LEGACY_SPELL_TYPE)) {
                    fixScrollData(spellTag);
                    return true;
                }
            }
        }

        return false;
    }

    private void fixScrollData(CompoundTag tag) {
        var legacySpellId = tag.getInt(SpellData.LEGACY_SPELL_TYPE);
        tag.remove(SpellData.LEGACY_SPELL_TYPE);
        tag.putString(SpellData.SPELL_ID, DataFixerHelpers.LEGACY_SPELL_MAPPING.getOrDefault(legacySpellId, "irons_spellbooks:none"));
    }
}
